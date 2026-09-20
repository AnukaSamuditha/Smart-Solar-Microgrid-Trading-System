package com.example.smart_solar_mgt_app.ui.prosumer.map

import com.example.smart_solar_mgt_app.domain.model.MicrogridNode

/**
 * "Map" tab content, implemented as a station list for now - no Google Maps API key is
 * available yet. NodeRepository is the same seam a real MapView would use later, so
 * swapping in the SDK won't require touching the data layer.
 */
sealed class MapUiState {
    data object Loading : MapUiState()
    data class Loaded(val nodes: List<MicrogridNode>) : MapUiState()
    data object Empty : MapUiState()
}
