package com.example.smart_solar_mgt_app.ui.prosumer.map

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smart_solar_mgt_app.data.repository.StationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapViewModel(private val stationRepository: StationRepository) : ViewModel() {

    private val _state = MutableLiveData<MapUiState>(MapUiState.Loading)
    val state: LiveData<MapUiState> = _state

    fun loadStations() {
        _state.value = MapUiState.Loading
        viewModelScope.launch {
            val stations = withContext(Dispatchers.IO) { stationRepository.getAllStations() }
            _state.value = if (stations.isEmpty()) MapUiState.Empty else MapUiState.Loaded(stations)
        }
    }
}
