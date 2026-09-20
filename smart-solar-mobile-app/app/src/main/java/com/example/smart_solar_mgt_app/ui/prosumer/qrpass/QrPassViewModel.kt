package com.example.smart_solar_mgt_app.ui.prosumer.qrpass

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionGenerateOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionGenerateRejection
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.data.repository.BookingRepository
import com.example.smart_solar_mgt_app.data.repository.NodeRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteTransactionRepository
import com.example.smart_solar_mgt_app.util.DateFormats
import com.example.smart_solar_mgt_app.util.QrBitmapEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Generates a fresh QR Energy Transfer Pass on-demand from the backend (POST
 * /api/v1/transactions/generate) each time this screen is opened, rather than reading a
 * once-signed local token - see RemoteTransactionRepository/TransactionService.GenerateAsync for
 * why generation is always-fresh (an opaque server-issued token, not a client-verifiable
 * signature) rather than idempotent.
 */
class QrPassViewModel(
    private val remoteTransactionRepository: RemoteTransactionRepository,
    private val bookingRepository: BookingRepository,
    private val nodeRepository: NodeRepository,
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
        val booking = bookingRepository.getBookingById(bookingId)
            ?: return QrPassUiState.Error("This reservation is no longer available.")
        if (booking.prosumerNic != nic) {
            return QrPassUiState.Error("This reservation does not belong to your account.")
        }

        return when (val outcome = remoteTransactionRepository.generate(bookingId)) {
            is RemoteTransactionGenerateOutcome.Success -> {
                val stationName = nodeRepository.getNodeById(booking.nodeId)?.name ?: "Unknown station"
                val expiryLabel = outcome.expiresAt.atZone(ZoneId.systemDefault())
                    .format(DateFormats.DISPLAY_DATE_TIME_FORMATTER)

                QrPassUiState.Loaded(
                    qrBitmap = QrBitmapEncoder.encode(outcome.token, QR_SIZE_PX),
                    stationName = stationName,
                    dateLabel = booking.bookingDate,
                    timeLabel = booking.bookingTime,
                    energyAmount = booking.energyAmount,
                    transactionId = outcome.transactionId,
                    expiryLabel = expiryLabel
                )
            }
            is RemoteTransactionGenerateOutcome.Rejected -> QrPassUiState.Error(messageFor(outcome.reason))
            is RemoteTransactionGenerateOutcome.NetworkFailure -> QrPassUiState.Error(outcome.message)
        }
    }

    private fun messageFor(reason: RemoteTransactionGenerateRejection): String = when (reason) {
        RemoteTransactionGenerateRejection.RESERVATION_NOT_FOUND -> "No active Energy Transfer Pass was found for this reservation."
        RemoteTransactionGenerateRejection.RESERVATION_NOT_CONFIRMED -> "This reservation has not been approved yet."
        RemoteTransactionGenerateRejection.RESERVATION_WINDOW_ELAPSED -> "This reservation's time window has already passed."
        RemoteTransactionGenerateRejection.UNKNOWN -> "Could not load the Energy Transfer Pass. Please try again."
    }

    private companion object {
        const val QR_SIZE_PX = 800
    }
}
