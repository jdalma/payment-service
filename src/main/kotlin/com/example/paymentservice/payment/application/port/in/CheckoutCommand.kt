package com.example.paymentservice.payment.application.port.`in`

/**
 * CheckoutRequest와 비슷하지만, 멱등성을 보장하기 위한 내부 키를 보유한다.
 * 같은 키를 보유한 Command는 오직 한 번만 처리된다.
 */
data class CheckoutCommand (
    val cartId: Long,
    val buyerId: Long,
    val productIds: List<Long>,
    val idempotencyKey: String
)
