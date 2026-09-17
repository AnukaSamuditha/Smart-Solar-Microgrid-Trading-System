package com.example.smart_solar_mgt_app.ui.gridoperator.bookings

import com.example.smart_solar_mgt_app.domain.model.BookingListItem

sealed class OperatorBookingsUiState {
    data object Loading : OperatorBookingsUiState()
    data class Loaded(val items: List<BookingListItem>) : OperatorBookingsUiState()
    data object EmptyScope : OperatorBookingsUiState()
    data object NoResults : OperatorBookingsUiState()
}
