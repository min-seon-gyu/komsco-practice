package com.komsco.voucher.voucher.interfaces

import com.komsco.voucher.common.idempotency.Idempotent
import com.komsco.voucher.voucher.application.VoucherIssueService
import com.komsco.voucher.voucher.application.VoucherRedemptionService
import com.komsco.voucher.voucher.application.VoucherRefundService
import com.komsco.voucher.voucher.application.VoucherWithdrawalService
import com.komsco.voucher.voucher.interfaces.dto.*
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/vouchers")
class VoucherController(
    private val issueService: VoucherIssueService,
    private val redemptionService: VoucherRedemptionService,
    private val refundService: VoucherRefundService,
    private val withdrawalService: VoucherWithdrawalService,
) {

    @PostMapping("/purchase")
    @ResponseStatus(HttpStatus.CREATED)
    @Idempotent
    fun purchase(@Valid @RequestBody request: PurchaseVoucherRequest): VoucherResponse =
        VoucherResponse.from(issueService.issue(request.memberId, request.regionId, request.faceValue))

    @PostMapping("/{id}/redeem")
    @Idempotent
    fun redeem(@PathVariable id: Long, @Valid @RequestBody request: RedeemRequest): RedemptionResult =
        redemptionService.redeem(id, request.merchantId, request.amount)

    @PostMapping("/{id}/refund")
    @Idempotent
    fun refund(@PathVariable id: Long, @RequestBody request: RefundRequest): VoucherResponse =
        VoucherResponse.from(refundService.refund(id, request.memberId))

    @PostMapping("/{id}/withdraw")
    @Idempotent
    fun withdraw(@PathVariable id: Long, @RequestBody request: WithdrawRequest): VoucherResponse =
        VoucherResponse.from(withdrawalService.withdraw(id, request.memberId))
}
