package com.example.smart_solar_mgt_app.core.security

import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session

/**
 * The only class UI and other managers call for auth/session/role/QR-token concerns.
 * Login flow: Login UI -> SecurityManager -> AuthRepository -> LocalDbManager -> SQLite.
 */
interface SecurityManager {
    fun login(nic: String, password: CharArray): LoginResult
    fun logout()
    fun registerHash(password: CharArray): String

    fun currentSession(): Session?
    fun isLoggedIn(): Boolean
    fun hasRole(role: Role): Boolean
    fun requireRole(role: Role)

    fun generateQrToken(transactionId: String, bookingId: String, ttlMillis: Long): String
    fun validateQrToken(token: String): QrValidationResult

    fun storeApiToken(token: String)
    fun getApiToken(): String?
    fun clearApiToken()
}