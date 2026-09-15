package com.example.smart_solar_mgt_app.domain.model

import com.example.smart_solar_mgt_app.core.common.SyncStatus

data class Booking(
    val bookingId: String,
    val prosumerNic: String,
    val stationId: String,
    val bookingDate: String, // ISO-8601, e.g. 2026-09-20
    val bookingTime: String, // 24h zero-padded, e.g. 14:30
    val energyAmount: Double,
    val status: BookingStatus,
    val syncStatus: SyncStatus,
    val createdAt: Long,
    val updatedAt: Long
)
