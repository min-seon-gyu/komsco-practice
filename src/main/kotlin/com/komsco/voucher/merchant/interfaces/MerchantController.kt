package com.komsco.voucher.merchant.interfaces

import com.komsco.voucher.merchant.application.MerchantService
import com.komsco.voucher.merchant.application.RegisterMerchantRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

data class MerchantResponse(
    val id: Long,
    val name: String,
    val businessNumber: String,
    val category: String,
    val regionId: Long,
    val status: String,
)

@RestController
@RequestMapping("/api/v1/merchants")
class MerchantController(
    private val merchantService: MerchantService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody request: RegisterMerchantRequest): MerchantResponse {
        val merchant = merchantService.register(request)
        return MerchantResponse(merchant.id, merchant.name, merchant.businessNumber, merchant.category.name, merchant.region.id, merchant.status.name)
    }

    @PostMapping("/{id}/approve")
    fun approve(@PathVariable id: Long): MerchantResponse {
        val merchant = merchantService.approve(id)
        return MerchantResponse(merchant.id, merchant.name, merchant.businessNumber, merchant.category.name, merchant.region.id, merchant.status.name)
    }

    @PostMapping("/{id}/reject")
    fun reject(@PathVariable id: Long): MerchantResponse {
        val merchant = merchantService.reject(id)
        return MerchantResponse(merchant.id, merchant.name, merchant.businessNumber, merchant.category.name, merchant.region.id, merchant.status.name)
    }
}
