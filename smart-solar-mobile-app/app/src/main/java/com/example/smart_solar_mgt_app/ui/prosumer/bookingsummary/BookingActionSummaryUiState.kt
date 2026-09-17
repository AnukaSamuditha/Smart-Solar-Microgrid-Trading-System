package com.example.smart_solar_mgt_app.ui.prosumer.bookingsummary

import com.example.smart_solar_mgt_app.domain.model.Booking

sealed class BookingActionSummaryUiState {
    data object Loading : BookingActionSummaryUiState()

    data class Loaded(
        val actionType: BookingActionType,
        val booking: Booking,
        val stationName: String
    ) : BookingActionSummaryUiState()

    data class Error(val message: String) : BookingActionSummaryUiState()
}
