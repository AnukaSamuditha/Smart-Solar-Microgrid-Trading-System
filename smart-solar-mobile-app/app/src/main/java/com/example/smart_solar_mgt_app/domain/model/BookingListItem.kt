package com.example.smart_solar_mgt_app.domain.model

import com.example.smart_solar_mgt_app.core.common.SyncStatus

/** Display-ready read-model for the Bookings list - joins bookings+stations in one query
 * so the adapter never needs a per-row station lookup. */
data class BookingListItem(
    val bookingId: String,
    val prosumerNic: String,
    val nodeId: String,
    val stationName: String,
    val bookingDate: String,
    val bookingTime: String,
    val energyAmount: Double,
    val status: BookingStatus,
    val syncStatus: SyncStatus
)
