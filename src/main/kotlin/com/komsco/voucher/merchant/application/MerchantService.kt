package com.komsco.voucher.merchant.application

import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import com.komsco.voucher.member.application.MemberService
import com.komsco.voucher.merchant.domain.Merchant
import com.komsco.voucher.merchant.domain.MerchantCategory
import com.komsco.voucher.merchant.domain.event.MerchantApprovedEvent
import com.komsco.voucher.merchant.infrastructure.MerchantJpaRepository
import com.komsco.voucher.region.application.RegionService
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

data class RegisterMerchantRequest(
    val name: String,
    val businessNumber: String,
    val category: String,
    val regionId: Long,
    val ownerId: Long,
)

@Service
@Transactional(readOnly = true)
class MerchantService(
    private val merchantRepository: MerchantJpaRepository,
    private val regionService: RegionService,
    private val memberService: MemberService,
    private val eventPublisher: ApplicationEventPublisher,
) {

    @Transactional
    fun register(request: RegisterMerchantRequest): Merchant {
        val region = regionService.getById(request.regionId)
        val owner = memberService.getById(request.ownerId)
        memberService.promoteToMerchantOwner(owner.id)

        return merchantRepository.save(
            Merchant(
                name = request.name,
                businessNumber = request.businessNumber,
                category = MerchantCategory.valueOf(request.category),
                region = region,
                owner = owner,
            )
        )
    }

    @Transactional
    fun approve(merchantId: Long): Merchant {
        val merchant = getById(merchantId)
        merchant.approve()
        eventPublisher.publishEvent(MerchantApprovedEvent(merchant.id, merchant.region.id))
        return merchant
    }

    @Transactional
    fun reject(merchantId: Long): Merchant {
        val merchant = getById(merchantId)
        merchant.reject()
        return merchant
    }

    fun getById(id: Long): Merchant =
        merchantRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.ENTITY_NOT_FOUND) }
}
