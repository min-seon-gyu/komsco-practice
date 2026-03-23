package com.komsco.voucher.support

import com.komsco.voucher.member.application.MemberService
import com.komsco.voucher.member.domain.Member
import com.komsco.voucher.member.interfaces.dto.RegisterMemberRequest
import com.komsco.voucher.merchant.application.MerchantService
import com.komsco.voucher.merchant.application.RegisterMerchantRequest
import com.komsco.voucher.merchant.domain.Merchant
import com.komsco.voucher.region.application.RegionService
import com.komsco.voucher.region.domain.Region
import com.komsco.voucher.region.interfaces.dto.CreateRegionRequest
import com.komsco.voucher.voucher.application.VoucherIssueService
import com.komsco.voucher.voucher.domain.Voucher
import com.komsco.voucher.voucher.infrastructure.VoucherJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class TestFixtures(
    private val regionService: RegionService,
    private val memberService: MemberService,
    private val merchantService: MerchantService,
    private val voucherIssueService: VoucherIssueService,
    private val voucherJpaRepository: VoucherJpaRepository,
) {
    private var counter = 0

    fun createRegion(
        name: String = "성남시",
        code: String = "SN",
        monthlyLimit: BigDecimal = BigDecimal("10000000000"),
        purchaseLimit: BigDecimal = BigDecimal("5000000"),
    ): Region {
        return regionService.create(
            CreateRegionRequest(
                name = name,
                regionCode = code,
                discountRate = BigDecimal("0.10"),
                purchaseLimitPerPerson = purchaseLimit,
                monthlyIssuanceLimit = monthlyLimit,
            )
        )
    }

    fun createMember(email: String? = null): Member {
        counter++
        return memberService.register(
            RegisterMemberRequest(
                email = email ?: "user$counter@test.com",
                name = "테스트유저$counter",
                password = "password123",
            )
        )
    }

    fun createMerchant(region: Region, owner: Member): Merchant {
        val merchant = merchantService.register(
            RegisterMerchantRequest(
                name = "테스트가게${counter++}",
                businessNumber = "123-45-${String.format("%05d", counter)}",
                category = "RESTAURANT",
                regionId = region.id,
                ownerId = owner.id,
            )
        )
        return merchantService.approve(merchant.id)
    }

    fun issueVoucher(
        memberId: Long,
        regionId: Long,
        faceValue: BigDecimal = BigDecimal("50000"),
    ): Voucher {
        return voucherIssueService.issue(memberId, regionId, faceValue)
    }

    @Transactional
    fun forceExpireVoucher(voucherId: Long) {
        voucherJpaRepository.updateExpiresAt(voucherId, LocalDateTime.now().minusDays(1))
    }

    @Transactional
    fun forcePurchasedAt(voucherId: Long, purchasedAt: LocalDateTime) {
        voucherJpaRepository.updatePurchasedAt(voucherId, purchasedAt)
    }
}
