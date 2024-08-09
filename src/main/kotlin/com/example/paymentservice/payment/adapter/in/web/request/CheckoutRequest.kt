package com.example.paymentservice.payment.adapter.`in`.web.request

import java.time.LocalDateTime

data class CheckoutRequest(
    val cartId: Long = 1,
    val productIds: List<Long> = listOf(1, 2, 3),
    val buyerId: Long = 1,
    val seed: String  = LocalDateTime.now().toString() // 요청을 구별해 줄 시드키이다.
)
