package com.example.smart_solar_mgt_app.ui.gridoperator.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.network.RemoteReservation
import com.example.smart_solar_mgt_app.core.network.RemoteReservationListOutcome
import com.example.smart_solar_mgt_app.data.repository.RemoteReservationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * Read state only - approve/reject actions are performed directly by the Fragment (via
 * BookingRepository, same pattern as BookingDetailFragment's cancel), which then calls load()
 * again to refresh. Talks directly to RemoteReservationRepository (bypassing BookingRepository's
 * local cache entirely) so every Backoffice/Grid Operator sees the same cross-prosumer Pending
 * queue regardless of which device/session created each request - mirrors
 * PendingProsumersViewModel's identical pattern for prosumer-registration approvals.
 */
class OperatorHomeViewModel(
    private val remoteReservationRepository: RemoteReservationRepository
) : ViewModel() {

    private val _state = MutableLiveData<OperatorHomeUiState>(OperatorHomeUiState.Loading)
    val state: LiveData<OperatorHomeUiState> = _state

    /** Optimistically removes an item right after its approve/reject succeeds (see
     * OperatorHomeFragment) rather than waiting for a full reload. */
    fun removeLocally(bookingId: String) {
        val current = _state.value
        if (current is OperatorHomeUiState.Loaded) {
            val remaining = current.items.filterNot { it.bookingId == bookingId }
            _state.value = if (remaining.isEmpty()) OperatorHomeUiState.Empty else OperatorHomeUiState.Loaded(remaining)
        }
    }

    fun load() {
        _state.value = OperatorHomeUiState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { remoteReservationRepository.listPending() }
            _state.value = when (outcome) {
                is RemoteReservationListOutcome.Success -> {
                    if (outcome.reservations.isEmpty()) {
                        OperatorHomeUiState.Empty
                    } else {
                        OperatorHomeUiState.Loaded(outcome.reservations.map { it.toPendingApprovalItem() }.sortedBy { it.bookingDate + it.bookingTime })
                    }
                }
                is RemoteReservationListOutcome.NetworkFailure ->
                    OperatorHomeUiState.Error("Can't reach the server. Check your connection and try again.")
            }
        }
    }

    private fun RemoteReservation.toPendingApprovalItem(): PendingApprovalItem {
        val zoned = startTime.atZone(ZoneId.systemDefault())
        return PendingApprovalItem(
            bookingId = id,
            prosumerNic = prosumerNic,
            stationName = nodeName ?: nodeId,
            bookingDate = zoned.toLocalDate().toString(),
            bookingTime = "%02d:%02d".format(zoned.hour, zoned.minute),
            energyAmount = energyAmount ?: 0.0
        )
    }
}
