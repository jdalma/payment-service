package com.example.paymentservice.payment.adapter.out.persistent.exception

import com.example.paymentservice.payment.domain.PaymentStatus
import java.lang.RuntimeException

class PaymentAlreadyProcessedException(
    val status: PaymentStatus,
    message: String
) : RuntimeException(message)
