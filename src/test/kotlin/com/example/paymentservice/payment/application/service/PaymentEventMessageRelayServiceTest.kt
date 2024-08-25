package com.example.paymentservice.payment.application.service

import com.example.paymentservice.payment.adapter.out.persistent.repository.PaymentOutboxRepository
import com.example.paymentservice.payment.application.port.out.DispatchEventMessagePort
import com.example.paymentservice.payment.application.port.out.LoadPendingPaymentEventMessagePort
import com.example.paymentservice.payment.application.port.out.PaymentStatusUpdateCommand
import com.example.paymentservice.payment.domain.PSPConfirmationStatus
import com.example.paymentservice.payment.domain.PaymentExecutionResult
import com.example.paymentservice.payment.domain.PaymentExtraDetails
import com.example.paymentservice.payment.domain.PaymentMethod
import com.example.paymentservice.payment.domain.PaymentType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import reactor.core.publisher.Hooks
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest
@Tag("ExternalIntegration")
@ActiveProfiles("local")
class PaymentEventMessageRelayServiceTest (
    @Autowired private val paymentOutboxRepository: PaymentOutboxRepository,
    @Autowired private val loadPendingPaymentEventMessagePort: LoadPendingPaymentEventMessagePort,
    @Autowired private val dispatchEventMessagePort: DispatchEventMessagePort
) {

    @Test
    fun `전송되지 않은 이벤트 메세지를 외부 시스템으로 전송한다`() {
        Hooks.onOperatorDebug()

        val paymentEventMessageRelayUseCase =  PaymentEventMessageRelayService(loadPendingPaymentEventMessagePort, dispatchEventMessagePort)

        val command = PaymentStatusUpdateCommand(
            paymentExecutionResult = PaymentExecutionResult(
                paymentKey = UUID.randomUUID().toString(),
                orderId = UUID.randomUUID().toString(),
                extraDetails = PaymentExtraDetails(
                    type = PaymentType.NORMAL,
                    method = PaymentMethod.EASY_PAY,
                    approvedAt = LocalDateTime.now(),
                    orderName = "test_order_name",
                    pspConfirmationStatus = PSPConfirmationStatus.DONE,
                    totalAmount = 50000L,
                    pspRawData = "{}"
                ),
                isSuccess = true,
                isFailure = false,
                isUnknown = false,
                isRetryable = false
            )
        )

        paymentOutboxRepository.insertOutbox(command).block()

        paymentEventMessageRelayUseCase.relay()

        Thread.sleep(10000)
    }
}
