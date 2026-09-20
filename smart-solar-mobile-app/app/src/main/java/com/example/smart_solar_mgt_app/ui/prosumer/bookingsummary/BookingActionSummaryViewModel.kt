package com.example.smart_solar_mgt_app.ui.prosumer.bookingsummary

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.NodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Re-fetches the booking by id rather than trusting anything passed through nav args, so the
 * summary always represents what was actually committed - not whatever the caller happened to
 * hold in memory at the moment of the write (same principle QrPassViewModel already follows).
 */
class BookingActionSummaryViewModel(
    private val bookingRepository: BookingRepository,
    private val nodeRepository: NodeRepository
) : ViewModel() {

    private val _state = MutableLiveData<BookingActionSummaryUiState>(BookingActionSummaryUiState.Loading)
    val state: LiveData<BookingActionSummaryUiState> = _state

    fun load(bookingId: String, actionType: BookingActionType) {
        _state.value = BookingActionSummaryUiState.Loading
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { buildState(bookingId, actionType) }
            _state.value = newState
        }
    }

    private fun buildState(bookingId: String, actionType: BookingActionType): BookingActionSummaryUiState {
        val booking = bookingRepository.getBookingById(bookingId)
            ?: return BookingActionSummaryUiState.Error("This reservation is no longer available.")
        val stationName = nodeRepository.getNodeById(booking.nodeId)?.name ?: booking.nodeId
        return BookingActionSummaryUiState.Loaded(actionType, booking, stationName)
    }
}
