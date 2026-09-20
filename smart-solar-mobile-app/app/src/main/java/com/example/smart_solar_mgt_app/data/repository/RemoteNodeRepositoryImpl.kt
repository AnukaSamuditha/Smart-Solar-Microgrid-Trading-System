package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.ApiConfig
import com.example.smart_solar_mgt_app.core.network.RemoteNodeListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteNodeOutcome
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import com.example.smart_solar_mgt_app.domain.model.BatterySlot
import com.example.smart_solar_mgt_app.domain.model.BatterySlotStatus
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
import com.example.smart_solar_mgt_app.domain.model.NodeStatus
import java.io.IOException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class RemoteNodeRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val secureSessionStore: SecureSessionStore,
    private val remoteAuthRepository: RemoteAuthRepository
) : RemoteNodeRepository {

    override fun listActiveNodes(): RemoteNodeListOutcome {
        val result = executeAuthenticated { authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/nodes/mine?pageSize=100").build() }
            ?: return RemoteNodeListOutcome.NetworkFailure("Unable to reach the server")

        if (result.code !in 200..299) {
            return RemoteNodeListOutcome.NetworkFailure("Server error (${result.code})")
        }

        val items = JSONObject(result.body).getJSONArray("items")
        val nodes = (0 until items.length()).map { index -> items.getJSONObject(index).toMicrogridNode() }
        return RemoteNodeListOutcome.Success(nodes)
    }

    override fun getNodeById(nodeId: String): RemoteNodeOutcome {
        val result = executeAuthenticated { authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/nodes/mine/$nodeId").build() }
            ?: return RemoteNodeOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code == 404 -> RemoteNodeOutcome.NotFound
            result.code in 200..299 -> RemoteNodeOutcome.Success(JSONObject(result.body).toMicrogridNode())
            else -> RemoteNodeOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    // { id, name, latitude, longitude, capacityKw, batterySlots: [{slotId, status}], status, ... } -
    // see MicrogridNodeResponse.FromEntity on the backend. Enum values (Available/Active etc.)
    // match the mobile enum names case-insensitively, so .uppercase() is enough, no mapping table.
    private fun JSONObject.toMicrogridNode(): MicrogridNode {
        val slotsJson = getJSONArray("batterySlots")
        val slots = (0 until slotsJson.length()).map { index ->
            val slot = slotsJson.getJSONObject(index)
            BatterySlot(slotId = slot.getString("slotId"), status = BatterySlotStatus.valueOf(slot.getString("status").uppercase()))
        }
        return MicrogridNode(
            nodeId = getString("id"),
            name = getString("name"),
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
            capacityKw = getDouble("capacityKw"),
            batterySlots = slots,
            status = NodeStatus.valueOf(getString("status").uppercase())
        )
    }

    // same 401-refresh-retry pattern as RemoteProsumerRepositoryImpl.executeAuthenticated
    private fun executeAuthenticated(buildRequest: () -> Request): HttpResult? {
        val first = executeOnce(buildRequest()) ?: return null
        if (first.code != 401) return first
        if (!remoteAuthRepository.refreshSession()) return first
        return executeOnce(buildRequest()) ?: first
    }

    private fun executeOnce(request: Request): HttpResult? = try {
        httpClient.newCall(request).execute().use { response ->
            HttpResult(response.code, response.body?.string().orEmpty())
        }
    } catch (e: IOException) {
        null
    }

    // attaches the stored prosumer bearer token - see SecureSessionStore.getApiToken. Read fresh
    // on every call (not cached) so a retry after refreshSession() picks up the new one.
    private fun authenticatedRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${secureSessionStore.getApiToken().orEmpty()}")

    private data class HttpResult(val code: Int, val body: String)
}
