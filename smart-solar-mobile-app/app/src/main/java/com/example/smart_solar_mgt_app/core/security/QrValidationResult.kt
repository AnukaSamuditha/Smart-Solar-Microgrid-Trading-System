package com.example.smart_solar_mgt_app.core.security

sealed class QrValidationResult {
    data class Valid(val transactionId: String, val bookingId: String) : QrValidationResult()
    data object Tampered : QrValidationResult()
    data object Expired : QrValidationResult()
}