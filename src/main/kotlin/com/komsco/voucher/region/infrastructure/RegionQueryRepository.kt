package com.komsco.voucher.region.infrastructure

import com.komsco.voucher.region.domain.QRegion
import com.komsco.voucher.region.domain.Region
import com.komsco.voucher.region.domain.RegionStatus
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

@Repository
class RegionQueryRepository(
    private val queryFactory: JPAQueryFactory,
) {
    private val region = QRegion.region

    fun findByStatus(status: RegionStatus): List<Region> {
        return queryFactory.selectFrom(region)
            .where(region.status.eq(status))
            .orderBy(region.name.asc())
            .fetch()
    }
}
