package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.RemoteNodeListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteNodeOutcome

/**
 * Prosumer-facing microgrid node/battery-slot browsing against the real Web Service
 * (smart-solar-mgt-api) - used to pick a node/slot when submitting a reservation request.
 */
interface RemoteNodeRepository {
    /** Active nodes only - GET /api/v1/nodes/mine. */
    fun listActiveNodes(): RemoteNodeListOutcome

    /** GET /api/v1/nodes/mine/{id} - 404 if missing or Deactivated. */
    fun getNodeById(nodeId: String): RemoteNodeOutcome
}
