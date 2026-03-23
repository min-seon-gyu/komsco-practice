package com.komsco.voucher.common.domain

import java.time.LocalDateTime
import java.util.UUID

abstract class DomainEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val occurredAt: LocalDateTime = LocalDateTime.now()
) {
    abstract val aggregateType: String
    abstract val aggregateId: Long
    abstract val eventType: String
}
