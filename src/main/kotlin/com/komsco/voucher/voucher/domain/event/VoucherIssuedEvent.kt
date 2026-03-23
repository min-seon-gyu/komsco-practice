package com.komsco.voucher.voucher.domain.event

import com.komsco.voucher.common.domain.DomainEvent
import java.math.BigDecimal

class VoucherIssuedEvent(
    override val aggregateId: Long,
    val memberId: Long,
    val regionId: Long,
    val faceValue: BigDecimal,
) : DomainEvent() {
    override val aggregateType = "VOUCHER"
    override val eventType = "VOUCHER_ISSUED"
}
