package com.example.smart_solar_mgt_app.ui.prosumer.qrpass

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import com.example.smart_solar_mgt_app.data.repository.TransactionRepository
import com.example.smart_solar_mgt_app.util.DateFormats
import com.example.smart_solar_mgt_app.util.QrBitmapEncoder
import com.example.smart_solar_mgt_app.util.TransactionRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class QrPassViewModel(
    private val transactionRepository: TransactionRepository,
    private val bookingRepository: BookingRepository,
    private val stationRepository: StationRepository,
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _state = MutableLiveData<QrPassUiState>(QrPassUiState.Loading)
    val state: LiveData<QrPassUiState> = _state

    fun load(bookingId: String) {
        val nic = securityManager.currentSession()?.userId
        if (nic == null) {
            _state.value = QrPassUiState.Error("You are not logged in.")
            return
        }
        _state.value = QrPassUiState.Loading
        viewModelScope.launch {
            val newState = withContext(Dispatchers.IO) { buildState(bookingId, nic) }
            _state.value = newState
        }
    }

    private fun buildState(bookingId: String, nic: String): QrPassUiState {
        return when (val result = transactionRepository.getEnergyTransferPass(bookingId, nic)) {
            is AppResult.Success -> {
                val transaction = result.data
                val booking = bookingRepository.getBookingById(bookingId)
                val stationName = booking?.let { stationRepository.getStationById(it.stationId)?.stationName }
                    ?: "Unknown station"
                val expiryLabel = Instant.ofEpochMilli(TransactionRules.expiryMillis(transaction.generatedAt))
                    .atZone(ZoneId.systemDefault())
                    .format(DateFormats.DISPLAY_DATE_TIME_FORMATTER)

                QrPassUiState.Loaded(
                    qrBitmap = QrBitmapEncoder.encode(transaction.qrToken, QR_SIZE_PX),
                    stationName = stationName,
                    dateLabel = booking?.bookingDate.orEmpty(),
                    timeLabel = booking?.bookingTime.orEmpty(),
                    energyAmount = booking?.energyAmount ?: 0.0,
                    transactionId = transaction.transactionId,
                    expiryLabel = expiryLabel
                )
            }
            is AppResult.Failure -> QrPassUiState.Error(messageFor(result.error))
        }
    }

    private fun messageFor(error: AppError): String = when (error) {
        AppError.NotFound -> "No active Energy Transfer Pass was found for this reservation."
        AppError.Unauthorized -> "This reservation does not belong to your account."
        AppError.InvalidStatusTransition -> "This reservation has not been approved yet."
        AppError.TooLateToModify -> "This reservation can no longer be modified."
        is AppError.Unknown -> error.message
        else -> "Could not load the Energy Transfer Pass. Please try again."
    }

    private companion object {
        const val QR_SIZE_PX = 800
    }
}
