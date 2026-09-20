package com.example.smart_solar_mgt_app.ui.prosumer.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.data.repository.NodeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(private val nodeRepository: NodeRepository) : ViewModel() {

    private val _state = MutableLiveData<MapUiState>(MapUiState.Loading)
    val state: LiveData<MapUiState> = _state

    /** Refreshes the node/slot cache from the backend when online, then shows whatever is
     * cached (works offline too, showing the last-known list - see NodeRepository). */
    fun loadStations() {
        _state.value = MapUiState.Loading
        viewModelScope.launch {
            val nodes = withContext(Dispatchers.IO) {
                nodeRepository.refreshFromRemote()
                nodeRepository.getAllNodes()
            }
            _state.value = if (nodes.isEmpty()) MapUiState.Empty else MapUiState.Loaded(nodes)
        }
    }
}
