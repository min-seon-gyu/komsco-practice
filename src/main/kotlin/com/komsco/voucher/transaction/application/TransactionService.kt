package com.komsco.voucher.transaction.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.transaction.domain.Transaction
import com.komsco.voucher.transaction.domain.TransactionType
import com.komsco.voucher.transaction.infrastructure.TransactionJpaRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Transactional(readOnly = true)
class TransactionService(
    private val transactionRepository: TransactionJpaRepository
) {

    @Transactional
    fun create(
        type: TransactionType,
        amount: BigDecimal,
        voucherId: Long? = null,
        merchantId: Long? = null,
        memberId: Long? = null,
        originalTransactionId: Long? = null,
    ): Transaction {
        return transactionRepository.save(
            Transaction(
                type = type,
                amount = amount,
                voucherId = voucherId,
                merchantId = merchantId,
                memberId = memberId,
                originalTransactionId = originalTransactionId,
            )
        )
    }

    fun getById(id: Long): Transaction =
        transactionRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.ENTITY_NOT_FOUND) }
}
