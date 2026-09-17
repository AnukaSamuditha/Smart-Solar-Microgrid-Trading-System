package com.example.smart_solar_mgt_app.util

import java.util.concurrent.TimeUnit

/**
 * Single source of truth for the Energy Transfer Pass QR validity window, so the signing side
 * (BookingRepositoryImpl.approveBooking) and the display side (QrPassViewModel) can't drift.
 */
object TransactionRules {
    const val TTL_HOURS = 24L

    fun expiryMillis(generatedAt: Long): Long = generatedAt + TimeUnit.HOURS.toMillis(TTL_HOURS)
}
