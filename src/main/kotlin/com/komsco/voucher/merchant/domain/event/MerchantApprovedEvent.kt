package com.komsco.voucher.merchant.domain.event

import com.komsco.voucher.common.domain.DomainEvent

class MerchantApprovedEvent(
    override val aggregateId: Long,
    val regionId: Long,
) : DomainEvent() {
    override val aggregateType = "MERCHANT"
    override val eventType = "MERCHANT_APPROVED"
}
