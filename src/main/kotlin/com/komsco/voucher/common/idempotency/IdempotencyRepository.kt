package com.komsco.voucher.common.idempotency

import org.springframework.data.jpa.repository.JpaRepository

interface IdempotencyRepository : JpaRepository<IdempotencyKey, Long> {
    fun findByIdempotencyKey(key: String): IdempotencyKey?
}
