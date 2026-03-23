package com.komsco.voucher.voucher.domain

import com.komsco.voucher.common.domain.BaseEntity
import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import jakarta.persistence.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime

@Entity
@Table(
    name = "vouchers",
    indexes = [
        Index(name = "idx_voucher_member", columnList = "memberId, status"),
        Index(name = "idx_voucher_region_status", columnList = "regionId, status, expiresAt"),
        Index(name = "idx_voucher_expiry", columnList = "status, expiresAt"),
    ]
)
class Voucher(
    @Column(nullable = false, unique = true, length = 19)
    val voucherCode: String,

    @Column(nullable = false)
    val faceValue: BigDecimal,

    @Column(nullable = false)
    var balance: BigDecimal,

    @Column(nullable = false)
    val memberId: Long,

    @Column(nullable = false)
    val regionId: Long,

    @Column(nullable = false)
    val purchasedAt: LocalDateTime,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    var status: VoucherStatus = VoucherStatus.ACTIVE,
) : BaseEntity() {

    val usageRatio: BigDecimal
        get() = if (faceValue > BigDecimal.ZERO)
            (faceValue - balance).divide(faceValue, 4, RoundingMode.HALF_UP)
        else BigDecimal.ZERO

    fun redeem(amount: BigDecimal) {
        if (!isUsable()) throw BusinessException(ErrorCode.VOUCHER_NOT_USABLE)
        if (isExpired()) throw BusinessException(ErrorCode.VOUCHER_EXPIRED)
        if (balance < amount) throw BusinessException(ErrorCode.INSUFFICIENT_BALANCE)

        balance -= amount
        status = if (balance == BigDecimal.ZERO) VoucherStatus.EXHAUSTED else VoucherStatus.PARTIALLY_USED
    }

    fun expire() {
        if (!isUsable()) throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION, "사용 가능 상태에서만 만료 처리할 수 있습니다")
        status = VoucherStatus.EXPIRED
    }

    fun requestRefund(refundThresholdRatio: BigDecimal) {
        if (status != VoucherStatus.PARTIALLY_USED)
            throw BusinessException(ErrorCode.REFUND_CONDITION_NOT_MET, "부분 사용된 상품권만 환불 가능합니다")
        if (usageRatio < refundThresholdRatio)
            throw BusinessException(ErrorCode.REFUND_CONDITION_NOT_MET)
        status = VoucherStatus.REFUND_REQUESTED
    }

    fun completeRefund(): BigDecimal {
        if (status != VoucherStatus.REFUND_REQUESTED)
            throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        val refundAmount = balance
        balance = BigDecimal.ZERO
        status = VoucherStatus.REFUNDED
        return refundAmount
    }

    fun requestWithdrawal(now: LocalDateTime = LocalDateTime.now()) {
        if (status != VoucherStatus.ACTIVE)
            throw BusinessException(ErrorCode.WITHDRAWAL_NOT_ALLOWED)
        if (purchasedAt.plusDays(7).isBefore(now))
            throw BusinessException(ErrorCode.WITHDRAWAL_PERIOD_EXPIRED)
        status = VoucherStatus.WITHDRAWAL_REQUESTED
    }

    fun completeWithdrawal(): BigDecimal {
        if (status != VoucherStatus.WITHDRAWAL_REQUESTED)
            throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        val refundAmount = balance
        balance = BigDecimal.ZERO
        status = VoucherStatus.WITHDRAWN
        return refundAmount
    }

    fun restoreBalance(amount: BigDecimal) {
        balance += amount
        status = if (balance == faceValue) VoucherStatus.ACTIVE else VoucherStatus.PARTIALLY_USED
    }

    fun isUsable(): Boolean = status in setOf(VoucherStatus.ACTIVE, VoucherStatus.PARTIALLY_USED)

    fun isExpired(): Boolean = expiresAt.isBefore(LocalDateTime.now())
}
