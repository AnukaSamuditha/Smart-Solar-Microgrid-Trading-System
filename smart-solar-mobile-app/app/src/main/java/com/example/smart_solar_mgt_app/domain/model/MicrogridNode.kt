package com.example.smart_solar_mgt_app.domain.model

/**
 * A solar charging node fetched from the backend (GET /api/v1/nodes/mine) and cached locally
 * (core/db/dao/CachedNodeDao.kt) so the prosumer's node/slot picker still works offline with the
 * last-known list. Individually-addressable battery slots, unlike the old local-only SolarStation
 * model's plain available-slots counter - a reservation books a specific slotId.
 */
data class MicrogridNode(
    val nodeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val capacityKw: Double,
    val batterySlots: List<BatterySlot>,
    val status: NodeStatus
)
