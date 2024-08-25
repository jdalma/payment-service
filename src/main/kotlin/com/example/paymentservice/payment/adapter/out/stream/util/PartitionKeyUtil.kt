package com.example.paymentservice.payment.adapter.out.stream.util

import org.springframework.stereotype.Component
import kotlin.math.abs

@Component
class PartitionKeyUtil {
    // 카프카의 payment 토픽 파티션 값이 6이다.
    val PARTITION_KEY_COUNT = 6

    fun createPartitionKey(number: Int): Int {
        return abs(number) % PARTITION_KEY_COUNT
    }
}
