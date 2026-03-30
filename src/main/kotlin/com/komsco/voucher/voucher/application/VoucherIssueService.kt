package com.komsco.voucher.voucher.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.ledger.application.LedgerService
import com.komsco.voucher.ledger.domain.AccountCode
import com.komsco.voucher.ledger.domain.LedgerEntryType
import com.komsco.voucher.region.application.RegionService
import com.komsco.voucher.region.domain.RegionStatus
import com.komsco.voucher.transaction.application.TransactionService
import com.komsco.voucher.transaction.domain.TransactionType
import com.komsco.voucher.voucher.domain.Voucher
import com.komsco.voucher.voucher.domain.VoucherCodeGenerator
import com.komsco.voucher.voucher.domain.event.VoucherIssuedEvent
import com.komsco.voucher.member.infrastructure.MemberJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherLockManager
import org.redisson.api.RedissonClient
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDateTime
import java.time.YearMonth

@Service
class VoucherIssueService(
    private val voucherRepository: VoucherJpaRepository,
    private val memberRepository: MemberJpaRepository,
    private val lockManager: VoucherLockManager,
    private val regionService: RegionService,
    private val codeGenerator: VoucherCodeGenerator,
    private val ledgerService: LedgerService,
    private val transactionService: TransactionService,
    private val redissonClient: RedissonClient,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun issue(memberId: Long, regionId: Long, faceValue: BigDecimal): Voucher {
        return lockManager.withMemberPurchaseLock(memberId) {
            // DB 비관적 락: Redis 장애 시에도 동일 회원 구매를 직렬화
            memberRepository.findByIdForUpdate(memberId)
                ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)

            val region = regionService.getById(regionId)

            if (region.status != RegionStatus.ACTIVE)
                throw BusinessException(ErrorCode.REGION_NOT_ACTIVE)

            // Check member purchase limit (DB 락 보유 상태에서 조회 → race condition 방지)
            val totalPurchased = voucherRepository.sumFaceValueByMemberAndRegion(memberId, regionId)
            if (totalPurchased + faceValue > region.policy.purchaseLimitPerPerson)
                throw BusinessException(ErrorCode.MEMBER_PURCHASE_LIMIT_EXCEEDED)

            // Check region monthly limit (Redis atomic counter)
            checkRegionMonthlyLimit(regionId, faceValue, region.policy.monthlyIssuanceLimit)

            // Generate voucher code
            val code = codeGenerator.generate(region.regionCode)

            // Create voucher (ACTIVE)
            val voucher = voucherRepository.save(
                Voucher(
                    voucherCode = code,
                    faceValue = faceValue,
                    balance = faceValue,
                    memberId = memberId,
                    regionId = regionId,
                    purchasedAt = LocalDateTime.now(),
                    expiresAt = LocalDateTime.now().plusMonths(6),
                )
            )

            // Create transaction + ledger (synchronous, same DB tx)
            val tx = transactionService.create(
                type = TransactionType.PURCHASE,
                amount = faceValue,
                voucherId = voucher.id,
                memberId = memberId,
            )
            ledgerService.record(
                debitAccount = AccountCode.VOUCHER_BALANCE,
                creditAccount = AccountCode.MEMBER_CASH,
                amount = faceValue,
                transactionId = tx.id,
                entryType = LedgerEntryType.PURCHASE,
            )
            tx.complete()

            // Publish event (audit log)
            eventPublisher.publishEvent(
                VoucherIssuedEvent(voucher.id, memberId, regionId, faceValue)
            )

            voucher
        }
    }

    private fun checkRegionMonthlyLimit(regionId: Long, amount: BigDecimal, limit: BigDecimal) {
        val key = "region:monthly:$regionId:${YearMonth.now()}"
        val counter = redissonClient.getAtomicLong(key)
        val newTotal = counter.addAndGet(amount.toLong())
        if (newTotal > limit.toLong()) {
            counter.addAndGet(-amount.toLong())
            throw BusinessException(ErrorCode.REGION_MONTHLY_LIMIT_EXCEEDED)
        }
        if (counter.remainTimeToLive() == -1L) {
            val endOfMonth = YearMonth.now().atEndOfMonth().plusDays(1)
            counter.expire(Duration.between(LocalDateTime.now(), endOfMonth.atStartOfDay()))
        }
    }
}
