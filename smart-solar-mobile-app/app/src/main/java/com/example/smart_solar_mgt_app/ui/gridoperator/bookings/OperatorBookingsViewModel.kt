package com.example.smart_solar_mgt_app.ui.gridoperator.bookings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.ui.prosumer.bookings.BookingScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Cross-prosumer read-only overview - same in-memory scope/search pattern as the Prosumer
 * BookingsViewModel, reusing its BookingScope enum, but searching by station name OR NIC since
 * an operator needs to find a specific prosumer's reservation, not just a station.
 */
class OperatorBookingsViewModel(
    private val bookingRepository: BookingRepository
) : ViewModel() {

    private val _state = MutableLiveData<OperatorBookingsUiState>(OperatorBookingsUiState.Loading)
    val state: LiveData<OperatorBookingsUiState> = _state

    private var allItems: List<BookingListItem> = emptyList()
    private var currentScope: BookingScope = BookingScope.PENDING
    private var currentQuery: String = ""

    fun load() {
        _state.value = OperatorBookingsUiState.Loading
        viewModelScope.launch {
            allItems = withContext(Dispatchers.IO) { bookingRepository.getAllBookingListItems() }
            applyFilters()
        }
    }

    fun onScopeSelected(scope: BookingScope) {
        currentScope = scope
        applyFilters()
    }

    fun onSearchChanged(query: String) {
        currentQuery = query
        applyFilters()
    }

    private fun applyFilters() {
        val scoped = allItems.filter { matchesScope(it, currentScope) }
        if (scoped.isEmpty()) {
            _state.value = OperatorBookingsUiState.EmptyScope
            return
        }
        val searched = if (currentQuery.isBlank()) {
            scoped
        } else {
            scoped.filter {
                it.stationName.contains(currentQuery, ignoreCase = true) ||
                    it.prosumerNic.contains(currentQuery, ignoreCase = true)
            }
        }
        _state.value = if (searched.isEmpty()) OperatorBookingsUiState.NoResults else OperatorBookingsUiState.Loaded(searched)
    }

    private fun matchesScope(item: BookingListItem, scope: BookingScope): Boolean = when (scope) {
        BookingScope.CURRENT -> item.status == BookingStatus.APPROVED
        BookingScope.PENDING -> item.status == BookingStatus.PENDING
        BookingScope.HISTORY -> item.status in setOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.REJECTED, BookingStatus.EXPIRED)
    }
}
