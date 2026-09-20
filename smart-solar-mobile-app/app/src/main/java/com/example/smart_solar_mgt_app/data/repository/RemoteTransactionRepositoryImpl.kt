package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.ApiConfig
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionCompleteOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionCompleteRejection
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionGenerateOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionGenerateRejection
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionScanOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionScanRejection
import com.example.smart_solar_mgt_app.core.network.RemoteTransactionVerification
import com.example.smart_solar_mgt_app.core.network.optNullableString
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import java.io.IOException
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RemoteTransactionRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val secureSessionStore: SecureSessionStore,
    private val remoteAuthRepository: RemoteAuthRepository
) : RemoteTransactionRepository {

    override fun generate(reservationId: String): RemoteTransactionGenerateOutcome {
        val requestBody = JSONObject().put("reservationId", reservationId).toString().toRequestBody(JSON_MEDIA_TYPE)

        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/transactions/generate").post(requestBody).build()
        } ?: return RemoteTransactionGenerateOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> {
                val json = JSONObject(result.body)
                RemoteTransactionGenerateOutcome.Success(
                    transactionId = json.getString("id"),
                    token = json.getString("token"),
                    expiresAt = Instant.parse(json.getString("expiresAt"))
                )
            }
            result.code == 404 -> RemoteTransactionGenerateOutcome.Rejected(RemoteTransactionGenerateRejection.RESERVATION_NOT_FOUND)
            result.code == 409 -> {
                val errorCode = runCatching { JSONObject(result.body).optString("error") }.getOrDefault("")
                val reason = when (errorCode) {
                    "ReservationNotConfirmed" -> RemoteTransactionGenerateRejection.RESERVATION_NOT_CONFIRMED
                    "ReservationWindowElapsed" -> RemoteTransactionGenerateRejection.RESERVATION_WINDOW_ELAPSED
                    else -> RemoteTransactionGenerateRejection.UNKNOWN
                }
                RemoteTransactionGenerateOutcome.Rejected(reason)
            }
            else -> RemoteTransactionGenerateOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    override fun scan(rawToken: String): RemoteTransactionScanOutcome {
        val requestBody = JSONObject().put("token", rawToken).toString().toRequestBody(JSON_MEDIA_TYPE)

        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/transactions/scan").post(requestBody).build()
        } ?: return RemoteTransactionScanOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> RemoteTransactionScanOutcome.Success(JSONObject(result.body).toRemoteTransactionVerification())
            result.code == 404 -> RemoteTransactionScanOutcome.Rejected(RemoteTransactionScanRejection.NOT_FOUND)
            result.code == 409 -> {
                val errorCode = runCatching { JSONObject(result.body).optString("error") }.getOrDefault("")
                val reason = when (errorCode) {
                    "AlreadyUsed" -> RemoteTransactionScanRejection.ALREADY_USED
                    "Expired" -> RemoteTransactionScanRejection.EXPIRED
                    "ReservationNoLongerConfirmed" -> RemoteTransactionScanRejection.RESERVATION_NO_LONGER_CONFIRMED
                    else -> RemoteTransactionScanRejection.UNKNOWN
                }
                RemoteTransactionScanOutcome.Rejected(reason)
            }
            else -> RemoteTransactionScanOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    override fun complete(transactionId: String): RemoteTransactionCompleteOutcome {
        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/transactions/$transactionId/complete")
                .patch("".toRequestBody(null))
                .build()
        } ?: return RemoteTransactionCompleteOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> RemoteTransactionCompleteOutcome.Success(JSONObject(result.body).toRemoteTransactionVerification())
            result.code == 404 -> RemoteTransactionCompleteOutcome.Rejected(RemoteTransactionCompleteRejection.NOT_FOUND)
            result.code == 409 -> RemoteTransactionCompleteOutcome.Rejected(RemoteTransactionCompleteRejection.NOT_SCANNED)
            else -> RemoteTransactionCompleteOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    // { id, reservationId, prosumerNic, prosumerFullName, nodeId, nodeName, slotId, startTime,
    //   endTime, energyAmount, status, generatedAt, expiresAt } - see TransactionVerificationResponse
    private fun JSONObject.toRemoteTransactionVerification(): RemoteTransactionVerification = RemoteTransactionVerification(
        transactionId = getString("id"),
        reservationId = getString("reservationId"),
        prosumerNic = getString("prosumerNic"),
        nodeName = optNullableString("nodeName"),
        startTime = optNullableString("startTime")?.let { Instant.parse(it) },
        endTime = optNullableString("endTime")?.let { Instant.parse(it) },
        energyAmount = if (isNull("energyAmount") || !has("energyAmount")) null else getDouble("energyAmount"),
        status = getString("status")
    )

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

    // attaches the stored prosumer/operator bearer token - see SecureSessionStore.getApiToken.
    // Read fresh on every call (not cached) so a retry after refreshSession() picks up the new one.
    private fun authenticatedRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${secureSessionStore.getApiToken().orEmpty()}")

    private data class HttpResult(val code: Int, val body: String)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
