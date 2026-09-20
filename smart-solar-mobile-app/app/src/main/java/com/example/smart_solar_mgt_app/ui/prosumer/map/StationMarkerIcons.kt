package com.example.smart_solar_mgt_app.ui.prosumer.map

import com.example.smart_solar_mgt_app.domain.model.NodeStatus
import com.google.android.gms.maps.model.BitmapDescriptorFactory

object StationMarkerIcons {
    fun hueFor(status: NodeStatus): Float = when (status) {
        NodeStatus.ACTIVE -> BitmapDescriptorFactory.HUE_GREEN
        NodeStatus.DEACTIVATED -> BitmapDescriptorFactory.HUE_AZURE
    }
}
