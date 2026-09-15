package com.example.smart_solar_mgt_app.domain.model

data class Transaction(
    val transactionId: String,
    val bookingId: String,
    val qrToken: String,
    val status: TransactionStatus,
    val operatorId: String?,
    val generatedAt: Long,
    val completedAt: Long?
)