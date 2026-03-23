package com.komsco.voucher.transaction.domain.event

import com.komsco.voucher.common.domain.DomainEvent
import java.math.BigDecimal

class TransactionCancelledEvent(
    override val aggregateId: Long,
    val voucherId: Long?,
    val cancelAmount: BigDecimal,
) : DomainEvent() {
    override val aggregateType = "TRANSACTION"
    override val eventType = "TRANSACTION_CANCELLED"
}
