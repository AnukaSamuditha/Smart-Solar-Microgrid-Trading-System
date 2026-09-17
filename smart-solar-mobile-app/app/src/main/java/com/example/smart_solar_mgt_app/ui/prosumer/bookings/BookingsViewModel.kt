package com.example.smart_solar_mgt_app.ui.prosumer.bookings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * One base query (BookingRepository.getBookingListItems) backs the whole screen; tab scope and
 * search are applied in-memory here rather than re-querying SQLite per keystroke/tab switch.
 */
class BookingsViewModel(
    private val bookingRepository: BookingRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<BookingsUiState>(BookingsUiState.Loading)
    val state: LiveData<BookingsUiState> = _state

    private var allItems: List<BookingListItem> = emptyList()
    private var currentScope: BookingScope = BookingScope.CURRENT
    private var currentQuery: String = ""
    private var currentDateFilter: LocalDate? = null

    fun load() {
        val nic = securityManager.currentSession()?.userId ?: return
        _state.value = BookingsUiState.Loading
        viewModelScope.launch {
            allItems = withContext(Dispatchers.IO) { bookingRepository.getBookingListItems(nic) }
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

    fun onDateFilterSelected(date: LocalDate) {
        currentDateFilter = date
        applyFilters()
    }

    fun onDateFilterCleared() {
        currentDateFilter = null
        applyFilters()
    }

    fun onFiltersCleared() {
        currentQuery = ""
        currentDateFilter = null
        applyFilters()
    }

    private fun applyFilters() {
        val scoped = allItems.filter { matchesScope(it, currentScope) }
        if (scoped.isEmpty()) {
            _state.value = BookingsUiState.EmptyScope
            return
        }
        var narrowed = scoped
        if (currentQuery.isNotBlank()) {
            narrowed = narrowed.filter { it.stationName.contains(currentQuery, ignoreCase = true) }
        }
        currentDateFilter?.let { date ->
            narrowed = narrowed.filter { it.bookingDate == date.toString() }
        }
        _state.value = if (narrowed.isEmpty()) BookingsUiState.NoResults else BookingsUiState.Loaded(narrowed)
    }

    private fun matchesScope(item: BookingListItem, scope: BookingScope): Boolean = when (scope) {
        BookingScope.CURRENT -> item.status == BookingStatus.APPROVED
        BookingScope.PENDING -> item.status == BookingStatus.PENDING
        BookingScope.HISTORY -> item.status in setOf(BookingStatus.COMPLETED, BookingStatus.CANCELLED, BookingStatus.EXPIRED)
    }
}
