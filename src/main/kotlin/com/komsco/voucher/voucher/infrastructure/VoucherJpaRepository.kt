package com.komsco.voucher.voucher.infrastructure

import com.komsco.voucher.voucher.domain.Voucher
import com.komsco.voucher.voucher.domain.VoucherStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDateTime

interface VoucherJpaRepository : JpaRepository<Voucher, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Voucher?

    fun findByMemberIdAndStatus(memberId: Long, status: VoucherStatus): List<Voucher>

    @Query("SELECT COALESCE(SUM(v.faceValue), 0) FROM Voucher v WHERE v.memberId = :memberId AND v.regionId = :regionId")
    fun sumFaceValueByMemberAndRegion(memberId: Long, regionId: Long): BigDecimal

    @Query("SELECT v.id FROM Voucher v WHERE v.status IN :statuses AND v.expiresAt < :now ORDER BY v.expiresAt ASC")
    fun findExpiredVoucherIds(
        @Param("statuses") statuses: List<VoucherStatus>,
        @Param("now") now: LocalDateTime,
        limit: Pageable,
    ): List<Long>
}
