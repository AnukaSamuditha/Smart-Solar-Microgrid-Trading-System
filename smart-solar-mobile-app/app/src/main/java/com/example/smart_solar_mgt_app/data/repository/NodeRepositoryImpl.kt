package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.network.RemoteNodeListOutcome
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode

class NodeRepositoryImpl(
    private val localDbManager: LocalDbManager,
    private val remoteNodeRepository: RemoteNodeRepository
) : NodeRepository {

    override fun getAllNodes(): List<MicrogridNode> = localDbManager.getCachedNodes()

    override fun getNodeById(nodeId: String): MicrogridNode? = localDbManager.getCachedNodeById(nodeId)

    override fun refreshFromRemote(): Boolean = when (val outcome = remoteNodeRepository.listActiveNodes()) {
        is RemoteNodeListOutcome.Success -> {
            localDbManager.replaceCachedNodes(outcome.nodes)
            true
        }
        is RemoteNodeListOutcome.NetworkFailure -> false
    }
}
