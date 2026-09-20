package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.ApiConfig
import com.example.smart_solar_mgt_app.core.network.RemoteReservation
import com.example.smart_solar_mgt_app.core.network.RemoteReservationActionOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationModifyRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationRequestOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationRequestRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationReviewOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationReviewRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationUpdateOutcome
import com.example.smart_solar_mgt_app.core.network.optNullableString
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import java.io.IOException
import java.time.Instant
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RemoteReservationRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val secureSessionStore: SecureSessionStore,
    private val remoteAuthRepository: RemoteAuthRepository
) : RemoteReservationRepository {

    override fun request(
        nodeId: String, slotId: String, startTime: Instant, endTime: Instant, energyAmount: Double?
    ): RemoteReservationRequestOutcome {
        val requestBody = JSONObject()
            .put("nodeId", nodeId)
            .put("slotId", slotId)
            .put("startTime", startTime.toString())
            .put("endTime", endTime.toString())
            .put("energyAmount", energyAmount)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations/mine").post(requestBody).build()
        } ?: return RemoteReservationRequestOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> RemoteReservationRequestOutcome.Success(JSONObject(result.body).toRemoteReservation())
            result.code == 400 || result.code == 404 || result.code == 409 -> parseRequestRejection(result.body)
            else -> RemoteReservationRequestOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    override fun update(reservationId: String, startTime: Instant, endTime: Instant): RemoteReservationUpdateOutcome {
        val requestBody = JSONObject()
            .put("startTime", startTime.toString())
            .put("endTime", endTime.toString())
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations/mine/$reservationId").patch(requestBody).build()
        } ?: return RemoteReservationUpdateOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> RemoteReservationUpdateOutcome.Success(JSONObject(result.body).toRemoteReservation())
            result.code == 404 -> RemoteReservationUpdateOutcome.Rejected(RemoteReservationModifyRejection.NOT_FOUND)
            result.code == 409 -> RemoteReservationUpdateOutcome.Rejected(parseModifyRejection(result.body))
            else -> RemoteReservationUpdateOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    override fun cancel(reservationId: String): RemoteReservationActionOutcome {
        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations/mine/$reservationId/cancel")
                .patch("".toRequestBody(null))
                .build()
        } ?: return RemoteReservationActionOutcome.NetworkFailure("Unable to reach the server")

        return when {
            result.code in 200..299 -> RemoteReservationActionOutcome.Success
            result.code == 404 -> RemoteReservationActionOutcome.Rejected(RemoteReservationModifyRejection.NOT_FOUND)
            result.code == 409 -> RemoteReservationActionOutcome.Rejected(parseModifyRejection(result.body))
            else -> RemoteReservationActionOutcome.NetworkFailure("Server error (${result.code})")
        }
    }

    override fun approve(reservationId: String): RemoteReservationReviewOutcome {
        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations/$reservationId/approve")
                .patch("".toRequestBody(null))
                .build()
        } ?: return RemoteReservationReviewOutcome.NetworkFailure("Unable to reach the server")
        return mapReviewResult(result)
    }

    override fun reject(reservationId: String, reason: String?): RemoteReservationReviewOutcome {
        val result = executeAuthenticated {
            val requestBody = JSONObject().put("reason", reason).toString().toRequestBody(JSON_MEDIA_TYPE)
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations/$reservationId/reject")
                .patch(requestBody)
                .build()
        } ?: return RemoteReservationReviewOutcome.NetworkFailure("Unable to reach the server")
        return mapReviewResult(result)
    }

    override fun listPending(): RemoteReservationListOutcome {
        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/reservations?status=Pending&pageSize=100&sortBy=StartTime&sortDir=asc").build()
        } ?: return RemoteReservationListOutcome.NetworkFailure("Unable to reach the server")

        if (result.code !in 200..299) {
            return RemoteReservationListOutcome.NetworkFailure("Server error (${result.code})")
        }

        val items = JSONObject(result.body).getJSONArray("items")
        val reservations = (0 until items.length()).map { index -> items.getJSONObject(index).toRemoteReservation() }
        return RemoteReservationListOutcome.Success(reservations)
    }

    private fun mapReviewResult(result: HttpResult): RemoteReservationReviewOutcome = when {
        result.code in 200..299 -> RemoteReservationReviewOutcome.Success
        result.code == 404 -> RemoteReservationReviewOutcome.Rejected(RemoteReservationReviewRejection.NOT_FOUND)
        result.code == 409 -> {
            val errorCode = runCatching { JSONObject(result.body).optString("error") }.getOrDefault("")
            val reason = when (errorCode) {
                "NotPending" -> RemoteReservationReviewRejection.NOT_PENDING
                "SlotNoLongerAvailable" -> RemoteReservationReviewRejection.SLOT_NO_LONGER_AVAILABLE
                else -> RemoteReservationReviewRejection.UNKNOWN
            }
            RemoteReservationReviewOutcome.Rejected(reason)
        }
        else -> RemoteReservationReviewOutcome.NetworkFailure("Server error (${result.code})")
    }

    // 400/404/409 error body: { "error": "NodeIdRequired" | "SlotIdRequired" | ... | "NodeNotFound" |
    // "SlotNotFound" | "ProsumerDeactivated" | "NodeDeactivated" | "SlotNotAvailable" }
    private fun parseRequestRejection(rawBody: String): RemoteReservationRequestOutcome {
        val errorCode = runCatching { JSONObject(rawBody).optString("error") }.getOrDefault("")
        val reason = when (errorCode) {
            "NodeNotFound" -> RemoteReservationRequestRejection.NODE_NOT_FOUND
            "SlotNotFound" -> RemoteReservationRequestRejection.SLOT_NOT_FOUND
            "ProsumerDeactivated" -> RemoteReservationRequestRejection.PROSUMER_DEACTIVATED
            "NodeDeactivated" -> RemoteReservationRequestRejection.NODE_DEACTIVATED
            "SlotNotAvailable" -> RemoteReservationRequestRejection.SLOT_NOT_AVAILABLE
            "NodeIdRequired", "SlotIdRequired", "StartTimeMustBeBeforeEndTime",
            "ReservationMustBeInFuture", "ReservationMustBeWithinSevenDays" -> RemoteReservationRequestRejection.INVALID_WINDOW
            else -> RemoteReservationRequestRejection.UNKNOWN
        }
        return RemoteReservationRequestOutcome.Rejected(reason)
    }

    // 409 error body: { "error": "AlreadyCancelled" | "AlreadyStarted" | "InsufficientNotice" }
    private fun parseModifyRejection(rawBody: String): RemoteReservationModifyRejection {
        val errorCode = runCatching { JSONObject(rawBody).optString("error") }.getOrDefault("")
        return when (errorCode) {
            "AlreadyCancelled" -> RemoteReservationModifyRejection.ALREADY_CANCELLED
            "AlreadyStarted" -> RemoteReservationModifyRejection.ALREADY_STARTED
            "InsufficientNotice" -> RemoteReservationModifyRejection.INSUFFICIENT_NOTICE
            else -> RemoteReservationModifyRejection.UNKNOWN
        }
    }

    // { id, prosumerNic, prosumerFullName, nodeId, nodeName, slotId, startTime, endTime,
    //   energyAmount, status, rejectionReason, createdAt, updatedAt } - see ReservationResponse
    private fun JSONObject.toRemoteReservation(): RemoteReservation = RemoteReservation(
        id = getString("id"),
        prosumerNic = getString("prosumerNic"),
        prosumerFullName = optNullableString("prosumerFullName"),
        nodeId = getString("nodeId"),
        nodeName = optNullableString("nodeName"),
        slotId = getString("slotId"),
        startTime = Instant.parse(getString("startTime")),
        endTime = Instant.parse(getString("endTime")),
        energyAmount = if (isNull("energyAmount") || !has("energyAmount")) null else getDouble("energyAmount"),
        status = getString("status"),
        rejectionReason = optNullableString("rejectionReason")
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

    // attaches the stored prosumer/staff bearer token - see SecureSessionStore.getApiToken. Read
    // fresh on every call (not cached) so a retry after refreshSession() picks up the new one.
    private fun authenticatedRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${secureSessionStore.getApiToken().orEmpty()}")

    private data class HttpResult(val code: Int, val body: String)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
