package com.example.paymentservice.payment.adapter.out.web.toss.executor

import com.example.paymentservice.payment.adapter.out.web.toss.exception.PSPConfirmationException
import com.example.paymentservice.payment.adapter.out.web.toss.exception.TossPaymentError
import com.example.paymentservice.payment.adapter.out.web.toss.response.TossFailureResponse
import com.example.paymentservice.payment.adapter.out.web.toss.response.TossPaymentConfirmationResponse
import com.example.paymentservice.payment.application.port.`in`.PaymentConfirmCommand
import com.example.paymentservice.payment.domain.PSPConfirmationStatus
import com.example.paymentservice.payment.domain.PaymentExecutionResult
import com.example.paymentservice.payment.domain.PaymentExtraDetails
import com.example.paymentservice.payment.domain.PaymentMethod
import com.example.paymentservice.payment.domain.PaymentType
import io.netty.handler.timeout.TimeoutException
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class TossPaymentExecutor (
    private val tossPaymentWebClient: WebClient,
    private val uri: String = "/v1/payments/confirm"
): PaymentExecutor {

    // 결제 승인을 요청할 때 멱등성을 보장하기 위해 요청 헤더에 멱등성 키를 첨부한다.
    // 이 키 값은 orderId가 될것이다.
    override fun execute(command: PaymentConfirmCommand): Mono<PaymentExecutionResult> {
        return tossPaymentWebClient.post()
            .uri(uri)
            .header("Idempotency-Key", command.orderId)
            .bodyValue("""
                {
                    "paymentKey": "${command.paymentKey}",
                    "orderId": "${command.orderId}",
                    "amount": "${command.amount}",
                    "" 
                }
            """.trimIndent())
            .retrieve()
            .onStatus({ statusCode: HttpStatusCode -> statusCode.is4xxClientError || statusCode.is5xxServerError}) { response ->
                response.bodyToMono(TossFailureResponse::class.java)
                    .flatMap {
                        val error = TossPaymentError.get(it.code)
                        Mono.error<PSPConfirmationException>(
                            PSPConfirmationException(
                                errorCode = error.name,
                                errorMessage = error.description,
                                isSuccess = error.isSuccess(),
                                isFailure = error.isFailure(),
                                isUnknown = error.isUnknown(),
                                isRetryableError = error.isRetryableError()
                            )
                        )
                    }
            }
            .bodyToMono(TossPaymentConfirmationResponse::class.java)
            .map {
                PaymentExecutionResult(
                    paymentKey = command.paymentKey,
                    orderId = command.orderId,
                    extraDetails = PaymentExtraDetails(
                        type = PaymentType.get(it.type),
                        method = PaymentMethod.get(it.method),
                        approvedAt = LocalDateTime.parse(it.approvedAt, DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                        pspRawData = it.toString(),
                        orderName = it.orderName,
                        pspConfirmationStatus = PSPConfirmationStatus.get(it.status),
                        totalAmount = it.totalAmount.toLong()
                    ),
                    isSuccess = true,
                    isFailure = false,
                    isUnknown = false,
                    isRetryable = false
                )
            }
            .retryWhen(
                Retry.backoff(2, Duration.ofSeconds(1))
                    .jitter(0.1)
                    .filter { (it is PSPConfirmationException && it.isRetryableError) || it is TimeoutException }
                    .doBeforeRetry { println("before retry hook: retryCount: ${it.totalRetries()} , errorCode: ${(it.failure() as PSPConfirmationException).errorCode}, isUnknown: ${(it.failure() as PSPConfirmationException).isUnknown}, isFailure: ${(it.failure() as PSPConfirmationException).isFailure}") }
                    .onRetryExhaustedThrow { _, retrySignal ->
                        retrySignal.failure()
                    }
            )
    }
}
