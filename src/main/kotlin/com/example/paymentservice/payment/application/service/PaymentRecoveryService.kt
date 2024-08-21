package com.example.paymentservice.payment.application.service

import com.example.paymentservice.common.UseCase
import com.example.paymentservice.payment.application.port.`in`.PaymentConfirmCommand
import com.example.paymentservice.payment.application.port.`in`.PaymentRecoveryUseCase
import com.example.paymentservice.payment.application.port.out.LoadPendingPaymentPort
import com.example.paymentservice.payment.application.port.out.PaymentExecutorPort
import com.example.paymentservice.payment.application.port.out.PaymentStatusUpdateCommand
import com.example.paymentservice.payment.application.port.out.PaymentStatusUpdatePort
import com.example.paymentservice.payment.application.port.out.PaymentValidationPort
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import reactor.core.scheduler.Schedulers
import java.util.concurrent.TimeUnit

@UseCase
@Profile("dev")
class PaymentRecoveryService(
    private val loadPendingPaymentPort: LoadPendingPaymentPort,
    private val paymentValidationPort: PaymentValidationPort,
    private val paymentExecutorPort: PaymentExecutorPort,
    private val paymentStatusUpdatePort: PaymentStatusUpdatePort,
    private val paymentErrorHandler: PaymentErrorHandler
): PaymentRecoveryUseCase {

    private val scheduler = Schedulers.newSingle("recovery")

    @Scheduled(fixedDelay = 180, initialDelay = 180, timeUnit = TimeUnit.SECONDS)
    override fun recovery() {
        loadPendingPaymentPort.getPendingPayments()
            .map {
                PaymentConfirmCommand(
                    paymentKey = it.paymentKey,
                    orderId = it.orderId,
                    amount = it.totalAmount()
                )
            }
            .parallel(2)
            .runOn(Schedulers.parallel())
            .flatMap { command ->
                paymentValidationPort.isValid(command.orderId, command.amount).thenReturn(command)
                    .flatMap { paymentExecutorPort.execute(it) }
                    .flatMap { paymentStatusUpdatePort.updatePaymentStatus(PaymentStatusUpdateCommand(it))  }
                    .onErrorResume { paymentErrorHandler.handlePaymentConfirmationError(it, command).thenReturn(true) }
            }
            .sequential()
            .subscribeOn(scheduler) // 이렇게 스케줄러를 단독으로 사용하여 다른 시스템에 영향을 끼치지 않는 것이 벌크 헤드 패턴이라고 한다.
            .subscribe()
    }
}
