package com.example.paymentservice.payment.adapter.out.stream

import com.example.paymentservice.common.Logger
import com.example.paymentservice.common.StreamAdapter
import com.example.paymentservice.payment.adapter.out.persistent.repository.PaymentOutboxRepository
import com.example.paymentservice.payment.application.port.out.DispatchEventMessagePort
import com.example.paymentservice.payment.domain.PaymentEventMessage
import com.example.paymentservice.payment.domain.PaymentEventMessageType
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.integration.IntegrationMessageHeaderAccessor
import org.springframework.integration.annotation.ServiceActivator
import org.springframework.integration.channel.FluxMessageChannel
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message
import org.springframework.messaging.support.MessageBuilder
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import reactor.core.publisher.Flux
import reactor.core.publisher.Sinks
import reactor.core.scheduler.Schedulers
import reactor.kafka.sender.SenderResult
import java.util.function.Supplier

@Configuration
@StreamAdapter
class PaymentEventMessageSender (
    private val paymentOutboxRepository: PaymentOutboxRepository
): DispatchEventMessagePort {

    // 특정 타입의 데이터를 동적으로 발행하기 위해 Sink를 사용한다. 발행된 메세지는 카프카로 전송될 것이다.
    // Sink는 리액티브 프로그래밍에서 데이터 스트림을 동적으로 생성하고 방출하는 메커니즘을 제공한다.
    private val sender = Sinks.many().unicast()
        .onBackpressureBuffer<Message<PaymentEventMessage>>()

    // 메세지 전송에 대한 결과 데이터인 SendResult를 동적으로 생성하고 방출하기 위한 Sink
    private val sendResult = Sinks.many().unicast().onBackpressureBuffer<SenderResult<String >>()

    @Bean
    fun send(): Supplier<Flux<Message<PaymentEventMessage>>> {
        return Supplier {
            sender.asFlux()
                .onErrorContinue { err , _ ->
                    Logger.error("sendEventMessage", err.message ?: "failed to send eventMessage", err)
                }
        }
    }

    // sendResult가 발행하는 데이터를 처리하기 위한 함수
    // 이 함수는 카프카로 보낸 메세지 전송 결과 데이터를 받아서 성공적으로 받은 데이터는 성공적으로 마킹하고 전송에 실패하는 메세지는 실패했다고 마킹한다.
    // 스프링 애플리케이션 컨텍스트가 초기화될 때 호출시켜 스트림을 구독하게 만들어 스트림을 처리하도록 하는 것이다.
    @PostConstruct
    fun handleSendResult() {
        sendResult.asFlux()
            .flatMap {
                when (it.recordMetadata() != null) {
                    true -> paymentOutboxRepository.markMessageAsSent(it.correlationMetadata(), PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS)
                    false -> paymentOutboxRepository.markMessageAsFailure(it.correlationMetadata(), PaymentEventMessageType.PAYMENT_CONFIRMATION_SUCCESS)
                }
            }
            .onErrorContinue { err, _ -> Logger.error("sendEventMessage", err.message ?: "failed to mark the outbox message.", err)  }
            .subscribeOn(Schedulers.newSingle("handle-send-result-event-message"))
            .subscribe()
    }


    // 카프카 메세지 발행 결과를 전달받을 채널 생성
    @Bean(name = ["payment-result"])
    fun sendResultChannel(): FluxMessageChannel {
        return FluxMessageChannel()
    }

    @ServiceActivator(inputChannel = "payment-result")
    fun receiveSendResult(results: SenderResult<String>) {
        // null 이 아니면 메세지 전송에 실패한것이다
        if (results.exception() != null) {
            Logger.error("sendEventMessage", results.exception().message ?: "receive an exception for event message send.", results.exception())
        }

        sendResult.emitNext(results, Sinks.EmitFailureHandler.FAIL_FAST)
    }

    // 릴레이에서 주기적으로 전송되기 때문에 메세지 발행에 실패하여도 재시도를 하지는 않는다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun dispatchAfterCommit(paymentEventMessage: PaymentEventMessage) {
        dispatch(paymentEventMessage)
    }

    override fun dispatch(paymentEventMessage: PaymentEventMessage) {
        sender.emitNext(createEventMessage(paymentEventMessage), Sinks.EmitFailureHandler.FAIL_FAST)
    }


    private fun createEventMessage(paymentEventMessage: PaymentEventMessage): Message<PaymentEventMessage> {
        return MessageBuilder.withPayload(paymentEventMessage)
            .setHeader(IntegrationMessageHeaderAccessor.CORRELATION_ID, paymentEventMessage.payload["orderId"])
            .setHeader(KafkaHeaders.PARTITION, paymentEventMessage.metadata["partitionKey"] ?: 0)
            .build()
    }
}
