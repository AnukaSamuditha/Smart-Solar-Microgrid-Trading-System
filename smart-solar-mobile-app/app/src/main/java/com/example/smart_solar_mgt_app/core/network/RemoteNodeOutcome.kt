package com.example.smart_solar_mgt_app.core.network

import com.example.smart_solar_mgt_app.domain.model.MicrogridNode

/** Result of GET /api/v1/nodes/mine - the prosumer-facing browse list (Active nodes only). */
sealed class RemoteNodeListOutcome {
    data class Success(val nodes: List<MicrogridNode>) : RemoteNodeListOutcome()
    data class NetworkFailure(val message: String) : RemoteNodeListOutcome()
}

/** Result of GET /api/v1/nodes/mine/{id}. */
sealed class RemoteNodeOutcome {
    data class Success(val node: MicrogridNode) : RemoteNodeOutcome()
    data object NotFound : RemoteNodeOutcome()
    data class NetworkFailure(val message: String) : RemoteNodeOutcome()
}
