package com.example.paymentservice.payment.domain

import java.time.LocalDateTime

/**
 * extraDetails와 failure는 결제 승인 성공 또는 실패 상황에서만 존재하기에 null이 가능하도록 지정한다.
 */
data class PaymentExecutionResult (
    val paymentKey: String,
    val orderId: String,
    val extraDetails: PaymentExtraDetails? = null, // 결제 승인 성공 시 받아오는 상세 데이터
    val failure: PaymentFailure? = null,    // 결제 승인 실패 시 에러에 대한 상세 데이터
    val isSuccess: Boolean,
    val isFailure: Boolean,
    val isUnknown: Boolean,
    val isRetryable: Boolean,
) {
    fun paymentStatus(): PaymentStatus {
        return when {
            isSuccess -> PaymentStatus.SUCCESS
            isFailure -> PaymentStatus.FAILURE
            isUnknown -> PaymentStatus.UNKNOWN
            else -> error("결제 (orderId: $orderId) 는 올바르지 않은 결제 상태입니다.")
        }
    }

    /**
     * 잘못된 상태를 가진 객체 생성을 방지하기 위함이다.
     */
    init {
        require(isSuccess || isFailure || isUnknown) {
            "결제 (orderId: $orderId) 는 올바르지 않은 결제 상태입니다."
        }
    }
}

data class PaymentExtraDetails (
    val type: PaymentType,
    val method: PaymentMethod,
    val approvedAt: LocalDateTime,
    val orderName: String,
    val pspConfirmationStatus: PSPConfirmationStatus,
    val totalAmount: Long,
    val pspRawData: String
)
