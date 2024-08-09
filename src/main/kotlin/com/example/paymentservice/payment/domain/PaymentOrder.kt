package com.example.paymentservice.payment.domain

data class PaymentOrder (
    val id: Long? = null,
    val paymentEventId: Long? = null,
    val sellerId: Long,
    val productId: Long,
    val orderId: String,
    val amount: Long,
    val paymentStatus: PaymentStatus,
    private var isLedgerUpdated: Boolean = false,
    private var isWalletUpdated: Boolean = false
) {

    fun isLedgerUpdated() = this.isLedgerUpdated
    fun isWalletUpdated() = this.isWalletUpdated
}
