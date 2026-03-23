package com.komsco.voucher.voucher.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.ledger.application.LedgerService
import com.komsco.voucher.ledger.domain.AccountCode
import com.komsco.voucher.ledger.domain.LedgerEntryType
import com.komsco.voucher.transaction.application.TransactionService
import com.komsco.voucher.transaction.domain.TransactionType
import com.komsco.voucher.voucher.domain.event.VoucherRedeemedEvent
import com.komsco.voucher.voucher.infrastructure.VoucherJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherLockManager
import com.komsco.voucher.voucher.interfaces.dto.RedemptionResult
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
class VoucherRedemptionService(
    private val voucherRepository: VoucherJpaRepository,
    private val lockManager: VoucherLockManager,
    private val ledgerService: LedgerService,
    private val transactionService: TransactionService,
    private val eventPublisher: ApplicationEventPublisher,
    private val meterRegistry: MeterRegistry,
) {

    @Transactional
    fun redeem(voucherId: Long, merchantId: Long, amount: BigDecimal): RedemptionResult {
        return lockManager.withVoucherLock(voucherId) {
            val timer = Timer.start(meterRegistry)
            try {
                val voucher = voucherRepository.findByIdForUpdate(voucherId)
                    ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)

                if (!voucher.isUsable()) throw BusinessException(ErrorCode.VOUCHER_NOT_USABLE)
                if (voucher.isExpired()) throw BusinessException(ErrorCode.VOUCHER_EXPIRED)
                if (voucher.balance < amount) throw BusinessException(ErrorCode.INSUFFICIENT_BALANCE)

                val previousBalance = voucher.balance
                voucher.redeem(amount)

                val tx = transactionService.create(
                    type = TransactionType.REDEMPTION,
                    amount = amount,
                    voucherId = voucherId,
                    merchantId = merchantId,
                )
                ledgerService.record(
                    debitAccount = AccountCode.MERCHANT_RECEIVABLE,
                    creditAccount = AccountCode.VOUCHER_BALANCE,
                    amount = amount,
                    transactionId = tx.id,
                    entryType = LedgerEntryType.REDEMPTION,
                )
                tx.complete()

                eventPublisher.publishEvent(
                    VoucherRedeemedEvent(voucherId, merchantId, amount, voucher.balance, tx.id, previousBalance)
                )

                meterRegistry.counter("voucher.redemption.count", "result", "success").increment()
                RedemptionResult(transactionId = tx.id, remainingBalance = voucher.balance)
            } catch (e: Exception) {
                meterRegistry.counter("voucher.redemption.count", "result", "failure").increment()
                throw e
            } finally {
                timer.stop(meterRegistry.timer("voucher.redemption.duration"))
            }
        }
    }
}
