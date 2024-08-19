package com.example.paymentservice.payment.application.port.`in`

/**
 * PSP 결제 승인 API를 요청하는데 필요한 데이터
 */
data class PaymentConfirmCommand (
    val paymentKey: String,
    val orderId: String,
    val amount: Long
)
