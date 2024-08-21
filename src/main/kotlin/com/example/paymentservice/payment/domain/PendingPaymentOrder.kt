package com.example.paymentservice.payment.domain

data class PendingPaymentOrder (
    val paymentOrderId: Long,
    val status: PaymentStatus,
    val amount: Long,
    val failedCount: Byte,  // 결제 실패 횟수
    val threshold: Byte     // 결제 실패 최대 허용 임계값
)
