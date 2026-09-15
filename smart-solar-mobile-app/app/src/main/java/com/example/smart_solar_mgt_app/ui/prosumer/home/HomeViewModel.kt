package com.example.smart_solar_mgt_app.ui.prosumer.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import com.example.smart_solar_mgt_app.domain.model.BookingCounts
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(
    private val bookingRepository: BookingRepository,
    private val stationRepository: StationRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<HomeUiState>(HomeUiState.Loading)
    val state: LiveData<HomeUiState> = _state

    fun loadDashboard() {
        val nic = securityManager.currentSession()?.userId ?: return
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { buildState(nic) }
            _state.value = newState
        }
    }

    private fun buildState(nic: String): HomeUiState {
        val rawCounts = bookingRepository.getStatusCounts(nic)
        val total = rawCounts.values.sum()
        if (total == 0) return HomeUiState.Empty

        val counts = BookingCounts(
            pending = rawCounts[BookingStatus.PENDING] ?: 0,
            confirmed = rawCounts[BookingStatus.CONFIRMED] ?: 0,
            completed = rawCounts[BookingStatus.COMPLETED] ?: 0
        )

        val upcomingBooking = bookingRepository.getUpcomingBooking(nic)
        val upcoming = upcomingBooking?.let { booking ->
            val stationName = stationRepository.getStationById(booking.stationId)?.stationName ?: "Unknown station"
            UpcomingBooking(
                stationName = stationName,
                dateLabel = booking.bookingDate,
                timeLabel = booking.bookingTime,
                energyAmount = booking.energyAmount,
                status = booking.status
            )
        }

        return HomeUiState.Loaded(welcomeName = nic, counts = counts, upcoming = upcoming)
    }
}
