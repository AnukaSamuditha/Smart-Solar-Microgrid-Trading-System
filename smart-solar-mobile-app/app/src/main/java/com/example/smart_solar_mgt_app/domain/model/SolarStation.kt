package com.example.smart_solar_mgt_app.domain.model

data class SolarStation(
    val stationId: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val capacityKwh: Double,
    val availableSlots: Int,
    val status: StationStatus
)
