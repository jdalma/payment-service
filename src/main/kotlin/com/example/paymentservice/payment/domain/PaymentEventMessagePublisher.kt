package com.example.paymentservice.payment.domain

import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.reactive.TransactionalEventPublisher
import reactor.core.publisher.Mono

@Component
class PaymentEventMessagePublisher (
    publisher: ApplicationEventPublisher
) {

    // 이 EventPublisher는 트랜잭션이 디비에 성공적으로 커밋된 후에 이벤트를 발행하는 공급자이다.
    // 그렇기에 이벤트를 발행하면 트랜잭신이 커밋되기까지 지연된다. 트랜잭션이 롤백되면 이벤트는 발행되지 않는다.
    private val transactionalEventPublisher = TransactionalEventPublisher(publisher)

    fun publishEvent(paymentEventMessage: PaymentEventMessage): Mono<PaymentEventMessage> {
        return transactionalEventPublisher.publishEvent(paymentEventMessage)
            .thenReturn(paymentEventMessage)
    }
}
