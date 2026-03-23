package com.komsco.voucher.voucher.domain.event

import com.komsco.voucher.common.domain.DomainEvent
import java.math.BigDecimal

class VoucherRedeemedEvent(
    override val aggregateId: Long,
    val merchantId: Long,
    val amount: BigDecimal,
    val remainingBalance: BigDecimal,
    val transactionId: Long,
) : DomainEvent() {
    override val aggregateType = "VOUCHER"
    override val eventType = "VOUCHER_REDEEMED"
}
