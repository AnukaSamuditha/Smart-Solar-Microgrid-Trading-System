package com.example.smart_solar_mgt_app.ui.gridoperator.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read state only - approve/reject actions are performed directly by the Fragment (same
 * pattern as BookingDetailFragment's cancel), which then calls load() again to refresh. */
class OperatorHomeViewModel(
    private val bookingRepository: BookingRepository,
    private val stationRepository: StationRepository
) : ViewModel() {

    private val _state = MutableLiveData<OperatorHomeUiState>(OperatorHomeUiState.Loading)
    val state: LiveData<OperatorHomeUiState> = _state

    fun load() {
        _state.value = OperatorHomeUiState.Loading
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { buildState() }
            _state.value = newState
        }
    }

    private fun buildState(): OperatorHomeUiState {
        val pending = bookingRepository.getAllPendingBookings()
        if (pending.isEmpty()) return OperatorHomeUiState.Empty

        val stationNames = stationRepository.getAllStations().associate { it.stationId to it.stationName }
        val items = pending
            .map { booking ->
                PendingApprovalItem(
                    bookingId = booking.bookingId,
                    prosumerNic = booking.prosumerNic,
                    stationName = stationNames[booking.stationId] ?: booking.stationId,
                    bookingDate = booking.bookingDate,
                    bookingTime = booking.bookingTime,
                    energyAmount = booking.energyAmount
                )
            }
            .sortedWith(compareBy({ it.bookingDate }, { it.bookingTime }))

        return OperatorHomeUiState.Loaded(items)
    }
}
