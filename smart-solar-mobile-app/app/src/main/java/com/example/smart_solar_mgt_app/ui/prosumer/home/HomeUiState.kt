package com.example.smart_solar_mgt_app.ui.prosumer.home

import com.example.smart_solar_mgt_app.domain.model.BookingCounts
import com.example.smart_solar_mgt_app.domain.model.BookingStatus

sealed class HomeUiState {
    data object Loading : HomeUiState()

    /** True empty state - this prosumer has never made a booking, any status. */
    data object Empty : HomeUiState()

    data class Loaded(
        val welcomeName: String,
        val counts: BookingCounts,
        val upcoming: UpcomingBooking?
    ) : HomeUiState()
}

data class UpcomingBooking(
    val bookingId: String,
    val stationName: String,
    val dateLabel: String,
    val timeLabel: String,
    val energyAmount: Double,
    val status: BookingStatus
)
