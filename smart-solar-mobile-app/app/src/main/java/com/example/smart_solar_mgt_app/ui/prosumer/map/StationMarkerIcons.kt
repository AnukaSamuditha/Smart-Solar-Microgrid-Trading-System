package com.example.smart_solar_mgt_app.ui.prosumer.map

import com.example.smart_solar_mgt_app.domain.model.StationStatus
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object StationMarkerIcons {
    fun hueFor(status: StationStatus): Float = when (status) {
        StationStatus.ACTIVE -> BitmapDescriptorFactory.HUE_GREEN
        StationStatus.FULL -> BitmapDescriptorFactory.HUE_ORANGE
        StationStatus.MAINTENANCE, StationStatus.OFFLINE -> BitmapDescriptorFactory.HUE_AZURE
    }
}
