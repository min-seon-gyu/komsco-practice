package com.komsco.voucher.transaction.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.ledger.application.LedgerService
import com.komsco.voucher.ledger.domain.AccountCode
import com.komsco.voucher.ledger.domain.LedgerEntryType
import com.komsco.voucher.transaction.domain.TransactionType
import com.komsco.voucher.transaction.domain.event.TransactionCancelledEvent
import com.komsco.voucher.transaction.infrastructure.TransactionJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherJpaRepository
import com.komsco.voucher.voucher.infrastructure.VoucherLockManager
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TransactionCancelService(
    private val transactionRepository: TransactionJpaRepository,
    private val transactionService: TransactionService,
    private val voucherRepository: VoucherJpaRepository,
    private val lockManager: VoucherLockManager,
    private val ledgerService: LedgerService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun cancel(transactionId: Long): Long {
        val original = transactionService.getById(transactionId)

        val voucherId = original.voucherId
            ?: throw BusinessException(ErrorCode.INVALID_INPUT, "상품권 거래만 취소할 수 있습니다")

        return lockManager.withVoucherLock(voucherId) {
            // 락 안에서 상태 변경 — 동시 취소 요청 직렬화
            original.requestCancel()

            // Create compensating transaction
            val compensating = transactionService.create(
                type = TransactionType.CANCELLATION,
                amount = original.amount,
                voucherId = voucherId,
                merchantId = original.merchantId,
                originalTransactionId = original.id,
            )

            // Reverse ledger entries (debit VOUCHER_BALANCE, credit MERCHANT_RECEIVABLE)
            ledgerService.record(
                debitAccount = AccountCode.VOUCHER_BALANCE,
                creditAccount = AccountCode.MERCHANT_RECEIVABLE,
                amount = original.amount,
                transactionId = compensating.id,
                entryType = LedgerEntryType.CANCELLATION,
            )
            compensating.complete()
            original.cancel()

            // Restore voucher balance
            val voucher = voucherRepository.findByIdForUpdate(voucherId)
                ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)
            voucher.restoreBalance(original.amount)

            eventPublisher.publishEvent(
                TransactionCancelledEvent(original.id, voucherId, original.amount)
            )

            compensating.id
        }
    }
}
