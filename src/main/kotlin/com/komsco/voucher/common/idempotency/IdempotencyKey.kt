package com.komsco.voucher.common.idempotency

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "idempotency_keys",
    indexes = [Index(name = "idx_idem_key", columnList = "idempotencyKey", unique = true)]
)
class IdempotencyKey(
    @Column(nullable = false, unique = true, length = 64)
    val idempotencyKey: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    var responseBody: String,

    @Column(nullable = false)
    var responseStatus: Int,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L
}
