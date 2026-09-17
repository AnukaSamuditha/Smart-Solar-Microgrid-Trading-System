package com.example.smart_solar_mgt_app.ui.prosumer.bookings

import com.example.smart_solar_mgt_app.domain.model.BookingListItem

sealed class BookingsUiState {
    data object Loading : BookingsUiState()
    data class Loaded(val items: List<BookingListItem>) : BookingsUiState()

    /** The active scope genuinely has no bookings at all - distinct from a search/filter yielding nothing. */
    data object EmptyScope : BookingsUiState()

    /** The scope has bookings, but the current search text excludes all of them. */
    data object NoResults : BookingsUiState()
}
