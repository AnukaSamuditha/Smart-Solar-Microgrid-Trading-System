package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.ApiConfig
import com.example.smart_solar_mgt_app.core.network.PendingProsumerRequest
import com.example.smart_solar_mgt_app.core.network.ProsumerProfile
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerListOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerProfileOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerRegisterOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerRegisterRejection
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerReviewOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerReviewRejection
import com.example.smart_solar_mgt_app.core.network.optNullableString
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RemoteProsumerRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val secureSessionStore: SecureSessionStore,
    private val remoteAuthRepository: RemoteAuthRepository
) : RemoteProsumerRepository {

    override fun register(nic: String, email: String, fullName: String, phone: String?, address: String?): RemoteProsumerRegisterOutcome {
        val requestBody = JSONObject()
            .put("nic", nic)
            .put("email", email)
            .put("fullName", fullName)
            .put("phone", phone)
            .put("address", address)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/v1/prosumers/register")
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> RemoteProsumerRegisterOutcome.Success
                    response.code == 400 || response.code == 409 -> parseRegisterRejection(rawBody)
                    else -> RemoteProsumerRegisterOutcome.NetworkFailure("Server error (${response.code})")
                }
            }
        } catch (e: IOException) {
            RemoteProsumerRegisterOutcome.NetworkFailure(e.message ?: "Unable to reach the server")
        }
    }

    override fun listPendingApproval(): RemoteProsumerListOutcome {
        val result = executeAuthenticated { authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/prosumers?status=PendingApproval&pageSize=100").build() }
            ?: return RemoteProsumerListOutcome.NetworkFailure("Unable to reach the server")

        if (result.code !in 200..299) {
            return RemoteProsumerListOutcome.NetworkFailure("Server error (${result.code})")
        }

        val items = JSONObject(result.body).getJSONArray("items")
        val requests = (0 until items.length()).map { index ->
            val item = items.getJSONObject(index)
            PendingProsumerRequest(
                nic = item.getString("nic"),
                fullName = item.optNullableString("fullName"),
                email = item.getString("email"),
                phone = item.optNullableString("phone"),
                address = item.optNullableString("address")
            )
        }
        return RemoteProsumerListOutcome.Success(requests)
    }

    override fun approve(nic: String): RemoteProsumerReviewOutcome {
        val result = executeAuthenticated {
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/prosumers/$nic/approve")
                .patch("".toRequestBody(null))
                .build()
        } ?: return RemoteProsumerReviewOutcome.NetworkFailure("Unable to reach the server")
        return mapReviewResult(result.code)
    }

    override fun deny(nic: String, reason: String?): RemoteProsumerReviewOutcome {
        val result = executeAuthenticated {
            val requestBody = JSONObject().put("reason", reason).toString().toRequestBody(JSON_MEDIA_TYPE)
            authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/prosumers/$nic/deny")
                .patch(requestBody)
                .build()
        } ?: return RemoteProsumerReviewOutcome.NetworkFailure("Unable to reach the server")
        return mapReviewResult(result.code)
    }

    override fun getMyProfile(): RemoteProsumerProfileOutcome {
        val result = executeAuthenticated { authenticatedRequest("${ApiConfig.BASE_URL}/api/v1/prosumers/me").build() }
            ?: return RemoteProsumerProfileOutcome.NetworkFailure("Unable to reach the server")

        if (result.code !in 200..299) {
            return RemoteProsumerProfileOutcome.NetworkFailure("Server error (${result.code})")
        }

        val json = JSONObject(result.body)
        val profile = ProsumerProfile(
            nic = json.getString("nic"),
            fullName = json.optNullableString("fullName"),
            email = json.getString("email"),
            phone = json.optNullableString("phone"),
            address = json.optNullableString("address")
        )
        return RemoteProsumerProfileOutcome.Success(profile)
    }

    private fun mapReviewResult(code: Int): RemoteProsumerReviewOutcome = when {
        code in 200..299 -> RemoteProsumerReviewOutcome.Success
        code == 401 || code == 403 -> RemoteProsumerReviewOutcome.Rejected(RemoteProsumerReviewRejection.UNAUTHORIZED)
        code == 404 -> RemoteProsumerReviewOutcome.Rejected(RemoteProsumerReviewRejection.NOT_FOUND)
        code == 409 -> RemoteProsumerReviewOutcome.Rejected(RemoteProsumerReviewRejection.NOT_PENDING_APPROVAL)
        else -> RemoteProsumerReviewOutcome.NetworkFailure("Server error ($code)")
    }

    /**
     * Sends an authenticated request; on a 401, attempts one token refresh
     * (RemoteAuthRepository.refreshSession) and retries once with the new token before giving
     * up - mirrors the web frontend's silent-refresh-and-retry interceptor
     * (smart-solar-mgt-fe/providers/api-client.ts). `buildRequest` is invoked again for the
     * retry so it picks up the freshly-saved access token (see authenticatedRequest).
     */
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

    // 400/409 error body: { "error": "InvalidNic" | "ValidEmailRequired" | "NicAlreadyInUse" | "EmailAlreadyInUse" }
    private fun parseRegisterRejection(rawBody: String): RemoteProsumerRegisterOutcome {
        val errorCode = runCatching { JSONObject(rawBody).optString("error") }.getOrDefault("")
        val reason = when (errorCode) {
            "InvalidNic" -> RemoteProsumerRegisterRejection.INVALID_NIC
            "ValidEmailRequired" -> RemoteProsumerRegisterRejection.INVALID_EMAIL
            "NicAlreadyInUse" -> RemoteProsumerRegisterRejection.NIC_ALREADY_IN_USE
            "EmailAlreadyInUse" -> RemoteProsumerRegisterRejection.EMAIL_ALREADY_IN_USE
            else -> RemoteProsumerRegisterRejection.UNKNOWN
        }
        return RemoteProsumerRegisterOutcome.Rejected(reason)
    }

    // attaches the stored Grid Operator/Backoffice bearer token - see SecureSessionStore.getApiToken.
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
