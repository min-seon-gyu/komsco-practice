package com.komsco.voucher.merchant.infrastructure

import com.komsco.voucher.merchant.domain.Merchant
import com.komsco.voucher.merchant.domain.MerchantStatus
import org.springframework.data.jpa.repository.JpaRepository

interface MerchantJpaRepository : JpaRepository<Merchant, Long> {
    fun findByRegionIdAndStatus(regionId: Long, status: MerchantStatus): List<Merchant>
}
