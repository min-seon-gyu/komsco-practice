package com.komsco.voucher.merchant.infrastructure

import com.komsco.voucher.merchant.domain.Settlement
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface SettlementJpaRepository : JpaRepository<Settlement, Long> {
    fun findByMerchantIdAndPeriodStartAndPeriodEnd(
        merchantId: Long,
        periodStart: LocalDate,
        periodEnd: LocalDate,
    ): Settlement?
}
