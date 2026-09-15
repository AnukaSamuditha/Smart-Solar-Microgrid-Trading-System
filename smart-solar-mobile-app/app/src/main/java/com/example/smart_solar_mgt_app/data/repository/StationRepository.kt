package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.domain.model.SolarStation

interface StationRepository {
    fun getStationById(stationId: String): SolarStation?
    fun getAllStations(): List<SolarStation>
    fun getAvailableStations(): List<SolarStation>
}
