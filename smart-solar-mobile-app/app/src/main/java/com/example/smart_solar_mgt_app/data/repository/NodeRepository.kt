package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.domain.model.MicrogridNode

/**
 * Prosumer-facing view over microgrid nodes/battery slots: reads from the local cache
 * (core/db/dao/CachedNodeDao.kt) so the picker/map still work offline, and refreshes that cache
 * from the backend (RemoteNodeRepository) when online. Replaces the old local-only
 * StationRepository now that node/slot data comes from the backend.
 */
interface NodeRepository {
    /** Reads whatever is currently cached - does not itself call the network. */
    fun getAllNodes(): List<MicrogridNode>

    fun getNodeById(nodeId: String): MicrogridNode?

    /** Fetches Active nodes from the backend and replaces the local cache on success. Returns
     * false on failure (offline/server error), leaving the existing cache untouched so callers
     * can still fall back to getAllNodes()/getNodeById(). */
    fun refreshFromRemote(): Boolean
}
