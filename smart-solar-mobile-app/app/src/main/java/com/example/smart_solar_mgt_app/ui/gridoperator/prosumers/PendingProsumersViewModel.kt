package com.example.smart_solar_mgt_app.ui.gridoperator.prosumers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerListOutcome
import com.example.smart_solar_mgt_app.data.repository.RemoteProsumerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Read state only - approve/deny actions are performed directly by the Fragment (same pattern
 * as OperatorHomeFragment's booking approve/reject), which then calls load() again to refresh. */
class PendingProsumersViewModel(private val remoteProsumerRepository: RemoteProsumerRepository) : ViewModel() {

    private val _state = MutableLiveData<PendingProsumersUiState>(PendingProsumersUiState.Loading)
    val state: LiveData<PendingProsumersUiState> = _state

    /** Optimistically removes an item right after its approve/deny is queued (see
     * PendingProsumersFragment) - the outbox may not sync for a while if offline, but the item
     * has already been acted on locally and shouldn't keep showing as actionable. */
    fun removeLocally(nic: String) {
        val current = _state.value
        if (current is PendingProsumersUiState.Loaded) {
            val remaining = current.items.filterNot { it.nic == nic }
            _state.value = if (remaining.isEmpty()) PendingProsumersUiState.Empty else PendingProsumersUiState.Loaded(remaining)
        }
    }

    fun load() {
        _state.value = PendingProsumersUiState.Loading
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) { remoteProsumerRepository.listPendingApproval() }
            _state.value = when (outcome) {
                is RemoteProsumerListOutcome.Success -> {
                    if (outcome.items.isEmpty()) {
                        PendingProsumersUiState.Empty
                    } else {
                        PendingProsumersUiState.Loaded(
                            outcome.items.map { PendingProsumerItem(it.nic, it.fullName, it.email, it.phone, it.address) }
                        )
                    }
                }
                is RemoteProsumerListOutcome.NetworkFailure ->
                    PendingProsumersUiState.Error("Can't reach the server. Check your connection and try again.")
            }
        }
    }
}
