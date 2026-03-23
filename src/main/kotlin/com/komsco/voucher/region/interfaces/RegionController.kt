package com.komsco.voucher.region.interfaces

import com.komsco.voucher.region.application.RegionService
import com.komsco.voucher.region.interfaces.dto.CreateRegionRequest
import com.komsco.voucher.region.interfaces.dto.RegionResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/regions")
class RegionController(
    private val regionService: RegionService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateRegionRequest): RegionResponse =
        RegionResponse.from(regionService.create(request))

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): RegionResponse =
        RegionResponse.from(regionService.getById(id))

    @GetMapping
    fun findAll(): List<RegionResponse> =
        regionService.findAll().map { RegionResponse.from(it) }
}
