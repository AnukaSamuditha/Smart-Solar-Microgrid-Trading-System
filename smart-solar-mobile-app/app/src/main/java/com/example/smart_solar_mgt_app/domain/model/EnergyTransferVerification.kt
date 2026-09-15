package com.example.smart_solar_mgt_app.domain.model

/**
 * Display-ready result of TransactionRepository.verifyToken - a read-only signature/expiry +
 * status check on a scanned QR, before the operator commits to completing it.
 */
data class EnergyTransferVerification(
    val transactionId: String,
    val bookingId: String,
    val prosumerNic: String,
    val stationName: String,
    val bookingDate: String,
    val bookingTime: String,
    val energyAmount: Double
)
