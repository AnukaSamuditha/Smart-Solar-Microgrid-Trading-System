package com.example.smart_solar_mgt_app.ui.prosumer.newbooking

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.domain.model.StationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

class NewBookingViewModel(
    private val bookingRepository: BookingRepository,
    private val stationRepository: StationRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _stations = MutableLiveData<List<SolarStation>>(emptyList())
    val stations: LiveData<List<SolarStation>> = _stations

    private val _formState = MutableLiveData<NewBookingFormState>(NewBookingFormState.Idle)
    val formState: LiveData<NewBookingFormState> = _formState

    /** Loads the pickable station list; if [preselectedStationId] isn't already ACTIVE with slots, it's still included so the screen can show why booking is blocked. */
    fun loadStations(preselectedStationId: String?) {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val available = stationRepository.getAvailableStations().associateBy { it.stationId }.toMutableMap()
                if (preselectedStationId != null && !available.containsKey(preselectedStationId)) {
                    stationRepository.getStationById(preselectedStationId)?.let { available[it.stationId] = it }
                }
                available.values.toList()
            }
            _stations.value = loaded
        }
    }

    fun onConfirmClicked(stationId: String?, date: LocalDate?, time: LocalTime?, energyAmountText: String) {
        val station = _stations.value?.firstOrNull { it.stationId == stationId }
        val errors = BookingValidator.validate(
            BookingValidator.Input(stationId, date, time, energyAmountText),
            station?.capacityKwh
        )
        if (errors.isNotEmpty()) {
            _formState.value = NewBookingFormState.FieldErrors(errors)
            return
        }

        _formState.value = NewBookingFormState.Saving
        viewModelScope.launch {
            val nic = securityManager.currentSession()?.userId ?: return@launch
            val outcome = withContext(Dispatchers.IO) {
                createBooking(nic, stationId!!, date!!, time!!, energyAmountText.trim().toDouble())
            }
            _formState.value = outcome
        }
    }

    fun onUpdateClicked(bookingId: String, stationId: String?, date: LocalDate?, time: LocalTime?, energyAmountText: String) {
        val station = _stations.value?.firstOrNull { it.stationId == stationId }
        val errors = BookingValidator.validate(
            BookingValidator.Input(stationId, date, time, energyAmountText),
            station?.capacityKwh
        )
        if (errors.isNotEmpty()) {
            _formState.value = NewBookingFormState.FieldErrors(errors)
            return
        }

        _formState.value = NewBookingFormState.Saving
        viewModelScope.launch {
            val nic = securityManager.currentSession()?.userId ?: return@launch
            val outcome = withContext(Dispatchers.IO) {
                updateBooking(bookingId, nic, station, date!!, time!!, energyAmountText.trim().toDouble())
            }
            _formState.value = outcome
        }
    }

    private fun updateBooking(
        bookingId: String,
        nic: String,
        station: SolarStation?,
        date: LocalDate,
        time: LocalTime,
        energyAmount: Double
    ): NewBookingFormState {
        val result = bookingRepository.updateBooking(bookingId, nic, date, time, energyAmount)
        return when (result) {
            is AppResult.Success -> {
                val resolvedStation = station ?: stationRepository.getStationById(result.data.stationId)
                if (resolvedStation != null) NewBookingFormState.Updated(result.data, resolvedStation) else NewBookingFormState.FormError("Could not update reservation. Please try again.")
            }
            is AppResult.Failure -> NewBookingFormState.FormError(
                when (result.error) {
                    AppError.TooLateToModify -> "This reservation can no longer be modified - less than 12 hours remain."
                    AppError.InvalidStatusTransition -> "This reservation has already been cancelled or completed and cannot be changed."
                    else -> "Could not update reservation. Please try again."
                }
            )
        }
    }

    private fun createBooking(
        nic: String,
        stationId: String,
        date: LocalDate,
        time: LocalTime,
        energyAmount: Double
    ): NewBookingFormState {
        // Fast-fail check before attempting the write - the atomic re-check inside
        // LocalDbManager.createBooking is what's actually race-safe, this is just UX.
        val freshStation = stationRepository.getStationById(stationId)
        if (freshStation == null || freshStation.status != StationStatus.ACTIVE || freshStation.availableSlots <= 0) {
            return NewBookingFormState.FormError("This station is no longer available. Please choose another.")
        }

        return when (val result = bookingRepository.createBooking(nic, stationId, date, time, energyAmount)) {
            is AppResult.Success -> NewBookingFormState.Created(result.data, freshStation)
            is AppResult.Failure -> NewBookingFormState.FormError("This station is no longer available. Please choose another.")
        }
    }
}
