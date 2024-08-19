package com.example.paymentservice.payment.adapter.out.persistent.repository

import com.example.paymentservice.payment.adapter.out.persistent.exception.PaymentAlreadyProcessedException
import com.example.paymentservice.payment.application.port.out.PaymentStatusUpdateCommand
import com.example.paymentservice.payment.domain.PaymentStatus
import com.example.paymentservice.payment.domain.PaymentStatus.*
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Repository
class R2DBCPaymentStatusUpdateRepository (
    private val databaseClient: DatabaseClient,
    private val transactionalOperator: TransactionalOperator
): PaymentStatusUpdateRepository {

    override fun updatePaymentStatusToExecuting(orderId: String, paymentKey: String): Mono<Boolean> {
        return checkPreviousPaymentOrderStatus(orderId)
            .flatMap { insertPaymentHistory(it, EXECUTING, "PAYMENT_CONFIRMATION_START") }
            .flatMap { updatePaymentOrderStatus(orderId, EXECUTING) }
            .flatMap { updatePaymentKey(orderId, paymentKey) }
            .`as`(transactionalOperator::transactional)
            .thenReturn(true)
    }

    // 승인 결과에 따라 결제 상태 저장 (성공과 실패, 알 수 없는 상태)
    override fun updatePaymentStatus(command: PaymentStatusUpdateCommand): Mono<Boolean> {
        return when (command.status) {
            SUCCESS -> updatePaymentStatusToSuccess(command)
            FAILURE -> updatePaymentStatusToFailure(command)
            UNKNOWN -> updatePaymentStatusToUnknown(command)
            else -> error("결제 상태 (status: ${command.status}) 는 올바르지 않은 결제 상태 입니다.")
        }
    }

    private fun updatePaymentStatusToSuccess(command: PaymentStatusUpdateCommand): Mono<Boolean> {
        return selectPaymentOrderStatus(command.orderId)
            .collectList()
            .flatMap { insertPaymentHistory(it, command.status, "PAYMENT_CONFIRMATION_DONE") }
            .flatMap { updatePaymentOrderStatus(command.orderId, command.status) }
            .flatMap { updatePaymentEventExtraDetails(command) }
            // .flatMap { paymentOutboxRepository.insertOutbox(command) }
            // .flatMap { paymentEventMessagePublisher.publishEvent(it) }
            .`as`(transactionalOperator::transactional)
            .thenReturn(true)
    }

    private fun updatePaymentEventExtraDetails(command: PaymentStatusUpdateCommand): Mono<Long> {
        return databaseClient.sql(UPDATE_PAYMENT_EVENT_EXTRA_DETAILS_QUERY)
            .bind("orderName", command.extraDetails!!.orderName)
            .bind("method", command.extraDetails.method.name)
            .bind("approvedAt", command.extraDetails.approvedAt.toString())
            .bind("orderId", command.orderId)
            .bind("type", command.extraDetails.type)
            .bind("pspRawData", command.extraDetails.pspRawData)
            .fetch()
            .rowsUpdated()
    }

    // command.failure 정보를 통째로 저장한다.
    private fun updatePaymentStatusToFailure(command: PaymentStatusUpdateCommand): Mono<Boolean> {
        return selectPaymentOrderStatus(command.orderId)
            .collectList()
            .flatMap { insertPaymentHistory(it, command.status, command.failure.toString()) }
            .flatMap { updatePaymentOrderStatus(command.orderId, command.status) }
            .`as`(transactionalOperator::transactional)
            .thenReturn(true)
    }

    // command.failure 정보를 통째로 저장한다.
    private fun updatePaymentStatusToUnknown(command: PaymentStatusUpdateCommand): Mono<Boolean> {
        return selectPaymentOrderStatus(command.orderId)
            .collectList()
            .flatMap { insertPaymentHistory(it, command.status, command.failure.toString()) }
            .flatMap { updatePaymentOrderStatus(command.orderId, command.status) }
            .flatMap { incrementPaymentOrderFailedCount(command) } // (updatePaymentStatusToFailure 와는 다르게) 결제 주문 실패 증가를 카운트한다.
            .`as`(transactionalOperator::transactional)
            .thenReturn(true)
    }

    private fun incrementPaymentOrderFailedCount(command: PaymentStatusUpdateCommand): Mono<Long> {
        return databaseClient.sql(INCREMENT_PAYMENT_ORDER_FAILED_COUNT_QUERY)
            .bind("orderId", command.orderId)
            .fetch()
            .rowsUpdated()
    }

    // EXECUTING 상태도 EXECUTING 상태로 변경하는 이유는 EXECUTING 상태로 변경하고 페이먼트 서비스가 갑자기 죽는 경우를 대비하기 위해서다.
    private fun checkPreviousPaymentOrderStatus(orderId: String): Mono<List<Pair<Long, String>>> {
        return selectPaymentOrderStatus(orderId)
            .handle { paymentOrder, sink ->
               when (paymentOrder.second) {
                    NOT_STARTED.name, UNKNOWN.name, EXECUTING.name -> {
                        sink.next(paymentOrder)
                    }
                    SUCCESS.name -> {
                        sink.error(PaymentAlreadyProcessedException(SUCCESS, "이미 처리 성공한 결제입니다."))
                    }
                   FAILURE.name -> {
                       sink.error(PaymentAlreadyProcessedException(FAILURE, "이미 처리 실패한 결제입니다."))
                   }
               }
            }
            .collectList()
    }

    private fun selectPaymentOrderStatus(orderId: String): Flux<Pair<Long, String>> {
        return databaseClient.sql(SELECT_PAYMENT_ORDER_STATUS_QUERY)
            .bind("orderId", orderId)
            .fetch()
            .all()
            .map { Pair(it["id"] as Long, it["payment_order_status"] as String) }
    }

    private fun insertPaymentHistory(paymentOrderItToStatus: List<Pair<Long, String>>, status: PaymentStatus, reason: String): Mono<Long> {
        if (paymentOrderItToStatus.isEmpty()) return Mono.empty()

        val valueClauses = paymentOrderItToStatus.joinToString(", ") {
            "( ${it.first}, '${it.second}', '${status}', '${reason}' )"
        }

        return databaseClient.sql(INSERT_PAYMENT_HISTORY_QUERY(valueClauses))
            .fetch()
            .rowsUpdated()
    }

    private fun updatePaymentOrderStatus(orderId: String, status: PaymentStatus): Mono<Long> {
        return databaseClient.sql(UPDATE_PAYMENT_ORDER_STATUS_QUERY)
            .bind("orderId", orderId)
            .bind("status", status)
            .fetch()
            .rowsUpdated()
    }

    private fun updatePaymentKey(orderId: String, paymentKey: String): Mono<Long> {
        return databaseClient.sql(UPDATE_PAYMENT_KEY_QUERY)
            .bind("paymentKey", paymentKey)
            .bind("orderId", orderId)
            .fetch()
            .rowsUpdated()
    }

    companion object {
        private val SELECT_PAYMENT_ORDER_STATUS_QUERY = """
          SELECT id, payment_order_status
          FROM payment_orders
          WHERE order_id = :orderId
        """.trimIndent()

        private val INSERT_PAYMENT_HISTORY_QUERY = fun (valueClauses: String) = """
          INSERT INTO payment_order_histories (payment_order_id, previous_status, new_status, reason)
          VALUES $valueClauses
        """.trimIndent()

        private val UPDATE_PAYMENT_ORDER_STATUS_QUERY = """
          UPDATE payment_orders
          SET payment_order_status = :status, updated_at = CURRENT_TIMESTAMP
          WHERE order_id = :orderId
        """.trimIndent()

        private val UPDATE_PAYMENT_KEY_QUERY = """
          UPDATE payment_events 
          SET payment_key = :paymentKey
          WHERE order_id = :orderId
        """.trimIndent()

        private val UPDATE_PAYMENT_EVENT_EXTRA_DETAILS_QUERY = """
          UPDATE payment_events
          SET order_name = :orderName, method = :method, approved_at = :approvedAt, type = :type, updated_at = CURRENT_TIMESTAMP, psp_raw_data = :pspRawData
          WHERE order_id = :orderId
        """.trimIndent()

        private val INCREMENT_PAYMENT_ORDER_FAILED_COUNT_QUERY = """
          UPDATE payment_orders
          SET failed_count = failed_count + 1 
          WHERE order_id = :orderId
        """.trimIndent()
    }
}
