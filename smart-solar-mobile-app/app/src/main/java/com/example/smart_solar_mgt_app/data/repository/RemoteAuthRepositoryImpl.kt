package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.ApiConfig
import com.example.smart_solar_mgt_app.core.network.RemoteAuthSession
import com.example.smart_solar_mgt_app.core.network.RemoteLoginOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteLoginRejection
import com.example.smart_solar_mgt_app.core.network.RemoteResetPasswordOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteResetPasswordRejection
import com.example.smart_solar_mgt_app.core.security.SecureSessionStore
import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class RemoteAuthRepositoryImpl(
    private val httpClient: OkHttpClient,
    private val secureSessionStore: SecureSessionStore
) : RemoteAuthRepository {

    override fun login(email: String, password: String): RemoteLoginOutcome {
        val requestBody = JSONObject()
            .put("email", email)
            .put("password", password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/v1/auth/login")
            .post(requestBody)
            .build()

        return executeLogin(request)
    }

    override fun prosumerLogin(nicOrEmail: String, password: String): RemoteLoginOutcome {
        val requestBody = JSONObject()
            .put("nicOrEmail", nicOrEmail)
            .put("password", password)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/v1/auth/prosumer/login")
            .post(requestBody)
            .build()

        return executeLogin(request)
    }

    override fun resetPassword(code: String, newPassword: String): RemoteResetPasswordOutcome {
        val requestBody = JSONObject()
            .put("token", code)
            .put("newPassword", newPassword)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/v1/auth/accept-invitation")
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val rawBody = response.body?.string().orEmpty()
                when {
                    response.isSuccessful -> RemoteResetPasswordOutcome.Success
                    response.code == 400 -> parseResetRejection(rawBody)
                    else -> RemoteResetPasswordOutcome.NetworkFailure("Server error (${response.code})")
                }
            }
        } catch (e: IOException) {
            RemoteResetPasswordOutcome.NetworkFailure(e.message ?: "Unable to reach the server")
        }
    }

    override fun refreshSession(): Boolean {
        val refreshToken = secureSessionStore.getRefreshToken() ?: return false
        val requestBody = JSONObject()
            .put("refreshToken", refreshToken)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("${ApiConfig.BASE_URL}/api/v1/auth/refresh")
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val json = JSONObject(response.body?.string().orEmpty())
                // refresh tokens rotate on every use (see backend RefreshTokenService) - the
                // presented one is now invalid, so the new one MUST be saved or the next refresh fails
                secureSessionStore.saveApiToken(json.getString("accessToken"))
                secureSessionStore.saveRefreshToken(json.getString("refreshToken"))
                true
            }
        } catch (e: IOException) {
            false
        }
    }

    // shared by login() and prosumerLogin() - both endpoints return the same LoginResponse/error shape
    private fun executeLogin(request: Request): RemoteLoginOutcome = try {
        httpClient.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            when {
                response.isSuccessful -> parseLoginSuccess(rawBody)
                response.code == 400 || response.code == 401 -> parseLoginRejection(rawBody)
                else -> RemoteLoginOutcome.NetworkFailure("Server error (${response.code})")
            }
        }
    } catch (e: IOException) {
        RemoteLoginOutcome.NetworkFailure(e.message ?: "Unable to reach the server")
    }

    // successful login: { accessToken, accessTokenExpiresAtUtc, refreshToken, refreshTokenExpiresAtUtc, role }
    private fun parseLoginSuccess(rawBody: String): RemoteLoginOutcome {
        val json = JSONObject(rawBody)
        val session = RemoteAuthSession(
            accessToken = json.getString("accessToken"),
            refreshToken = json.getString("refreshToken"),
            role = json.getString("role")
        )
        return RemoteLoginOutcome.Success(session)
    }

    // 400/401 error body: { "error": "InvalidCredentials" | "ProfileIncomplete" | "AccountDeactivated" |
    // "ProsumerMobileOnly" | "PendingApproval" | "AccountCreationDenied" | "PasswordNotSet" | ... }
    private fun parseLoginRejection(rawBody: String): RemoteLoginOutcome {
        val errorCode = runCatching { JSONObject(rawBody).optString("error") }.getOrDefault("")
        val reason = when (errorCode) {
            "InvalidCredentials" -> RemoteLoginRejection.INVALID_CREDENTIALS
            "ProfileIncomplete" -> RemoteLoginRejection.PROFILE_INCOMPLETE
            "AccountDeactivated" -> RemoteLoginRejection.ACCOUNT_DEACTIVATED
            "ProsumerMobileOnly" -> RemoteLoginRejection.PROSUMER_MOBILE_ONLY
            "PendingApproval" -> RemoteLoginRejection.PENDING_APPROVAL
            "AccountCreationDenied" -> RemoteLoginRejection.ACCOUNT_CREATION_DENIED
            "PasswordNotSet" -> RemoteLoginRejection.PASSWORD_NOT_SET
            else -> RemoteLoginRejection.UNKNOWN
        }
        return RemoteLoginOutcome.Rejected(reason)
    }

    // 400 error body from accept-invitation: { "error": "NotFound" | "Expired" | "AlreadyUsed" | "InvalidToken" | "InvalidRequest" }
    private fun parseResetRejection(rawBody: String): RemoteResetPasswordOutcome {
        val errorCode = runCatching { JSONObject(rawBody).optString("error") }.getOrDefault("")
        val reason = when (errorCode) {
            "NotFound", "InvalidToken" -> RemoteResetPasswordRejection.INVALID_CODE
            "Expired" -> RemoteResetPasswordRejection.EXPIRED
            "AlreadyUsed" -> RemoteResetPasswordRejection.ALREADY_USED
            else -> RemoteResetPasswordRejection.UNKNOWN
        }
        return RemoteResetPasswordOutcome.Rejected(reason)
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
