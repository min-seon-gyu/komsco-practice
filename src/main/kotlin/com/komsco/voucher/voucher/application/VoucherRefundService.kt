package com.komsco.voucher.voucher.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.ledger.application.LedgerService
import com.komsco.voucher.ledger.domain.AccountCode
import com.komsco.voucher.ledger.domain.LedgerEntryType
import com.komsco.voucher.region.application.RegionService
import com.komsco.voucher.transaction.application.TransactionService
import com.komsco.voucher.transaction.domain.TransactionType
import com.komsco.voucher.voucher.domain.Voucher
import com.komsco.voucher.voucher.domain.event.VoucherRefundedEvent
import com.komsco.voucher.voucher.infrastructure.VoucherJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherLockManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VoucherRefundService(
    private val voucherRepository: VoucherJpaRepository,
    private val lockManager: VoucherLockManager,
    private val regionService: RegionService,
    private val ledgerService: LedgerService,
    private val transactionService: TransactionService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun refund(voucherId: Long, memberId: Long): Voucher {
        return lockManager.withVoucherLock(voucherId) {
            val voucher = voucherRepository.findByIdForUpdate(voucherId)
                ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)

            if (voucher.memberId != memberId)
                throw BusinessException(ErrorCode.INVALID_INPUT, "본인의 상품권만 환불할 수 있습니다")

            val region = regionService.getById(voucher.regionId)
            voucher.requestRefund(region.policy.refundThresholdRatio)
            val refundAmount = voucher.completeRefund()

            val tx = transactionService.create(
                type = TransactionType.REFUND,
                amount = refundAmount,
                voucherId = voucherId,
                memberId = memberId,
            )
            ledgerService.record(
                debitAccount = AccountCode.REFUND_PAYABLE,
                creditAccount = AccountCode.VOUCHER_BALANCE,
                amount = refundAmount,
                transactionId = tx.id,
                entryType = LedgerEntryType.REFUND,
            )
            tx.complete()

            eventPublisher.publishEvent(
                VoucherRefundedEvent(voucherId, memberId, refundAmount, tx.id)
            )

            voucher
        }
    }
}
