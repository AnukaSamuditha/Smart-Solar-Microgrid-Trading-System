package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.domain.model.SolarStation

class StationRepositoryImpl(private val localDbManager: LocalDbManager) : StationRepository {
    override fun getStationById(stationId: String): SolarStation? = localDbManager.getStationById(stationId)
    override fun getAllStations(): List<SolarStation> = localDbManager.getAllStations()
    override fun getAvailableStations(): List<SolarStation> = localDbManager.getAvailableStations()
}
