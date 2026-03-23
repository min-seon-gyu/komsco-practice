package com.komsco.voucher.region.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.region.domain.Region
import com.komsco.voucher.region.domain.RegionPolicy
import com.komsco.voucher.region.domain.SettlementPeriod
import com.komsco.voucher.region.infrastructure.RegionJpaRepository
import com.komsco.voucher.region.interfaces.dto.CreateRegionRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class RegionService(
    private val regionRepository: RegionJpaRepository
) {

    @Transactional
    fun create(request: CreateRegionRequest): Region {
        val policy = RegionPolicy(
            discountRate = request.discountRate,
            purchaseLimitPerPerson = request.purchaseLimitPerPerson,
            monthlyIssuanceLimit = request.monthlyIssuanceLimit,
            refundThresholdRatio = request.refundThresholdRatio,
            settlementPeriod = SettlementPeriod.valueOf(request.settlementPeriod)
        )
        return regionRepository.save(
            Region(
                name = request.name,
                regionCode = request.regionCode,
                policy = policy
            )
        )
    }

    fun getById(id: Long): Region =
        regionRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.ENTITY_NOT_FOUND) }

    fun getByCode(code: String): Region =
        regionRepository.findByRegionCode(code)
            ?: throw BusinessException(ErrorCode.ENTITY_NOT_FOUND)

    fun findAll(): List<Region> = regionRepository.findAll()
}
