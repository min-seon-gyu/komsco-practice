package com.komsco.voucher.merchant.domain

import com.komsco.voucher.common.domain.BaseEntity
import com.komsco.voucher.common.exception.BusinessException
import com.komsco.voucher.common.exception.ErrorCode
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.LocalDate

enum class SettlementStatus {
    PENDING, CONFIRMED, PAID, DISPUTED
}

@Entity
@Table(
    name = "settlements",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_settlement_period", columnNames = ["merchantId", "periodStart", "periodEnd"])
    ]
)
class Settlement(
    @Column(nullable = false)
    val merchantId: Long,

    @Column(nullable = false)
    val periodStart: LocalDate,

    @Column(nullable = false)
    val periodEnd: LocalDate,

    @Column(nullable = false)
    var totalAmount: BigDecimal,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    var status: SettlementStatus = SettlementStatus.PENDING,

    var disputeReason: String? = null,
) : BaseEntity() {

    fun confirm() {
        if (status != SettlementStatus.PENDING && status != SettlementStatus.DISPUTED)
            throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        status = SettlementStatus.CONFIRMED
        disputeReason = null
    }

    fun dispute(reason: String) {
        if (status != SettlementStatus.PENDING)
            throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        status = SettlementStatus.DISPUTED
        disputeReason = reason
    }

    fun pay() {
        if (status != SettlementStatus.CONFIRMED)
            throw BusinessException(ErrorCode.INVALID_STATE_TRANSITION)
        status = SettlementStatus.PAID
    }
}
