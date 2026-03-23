package com.komsco.voucher.voucher.interfaces.dto

import java.math.BigDecimal

data class PurchaseVoucherRequest(
    val memberId: Long,
    val regionId: Long,
    val faceValue: BigDecimal,
)

data class RedeemRequest(
    val merchantId: Long,
    val amount: BigDecimal,
)

data class RefundRequest(
    val memberId: Long,
)

data class WithdrawRequest(
    val memberId: Long,
)
