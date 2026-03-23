package com.komsco.voucher.region.infrastructure

import com.komsco.voucher.region.domain.Region
import org.springframework.data.jpa.repository.JpaRepository

interface RegionJpaRepository : JpaRepository<Region, Long> {
    fun findByRegionCode(regionCode: String): Region?
}
