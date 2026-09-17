package com.example.smart_solar_mgt_app.core.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.smart_solar_mgt_app.domain.model.LoginState
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session

/**
 * Authoritative session + token storage (EncryptedSharedPreferences). Every authorization
 * decision reads from here - the plain SQLite `session` table (LocalDbManager) is only a
 * non-authoritative mirror for convenience reads, never trusted for auth decisions.
 */
class SecureSessionStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSession(session: Session) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_ROLE, session.role.name)
            .putString(KEY_LOGIN_STATE, session.loginState.name)
            .apply()
    }

    fun getSession(): Session? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val roleName = prefs.getString(KEY_ROLE, null) ?: return null
        val loginStateName = prefs.getString(KEY_LOGIN_STATE, null) ?: return null
        return Session(userId, Role.valueOf(roleName), LoginState.valueOf(loginStateName))
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_USER_ID)
            .remove(KEY_ROLE)
            .remove(KEY_LOGIN_STATE)
            .apply()
    }

    fun saveApiToken(token: String) {
        prefs.edit().putString(KEY_API_TOKEN, token).apply()
    }

    fun getApiToken(): String? = prefs.getString(KEY_API_TOKEN, null)

    fun clearApiToken() {
        prefs.edit().remove(KEY_API_TOKEN).apply()
    }

    private companion object {
        const val PREFS_NAME = "secure_session_prefs"
        const val KEY_USER_ID = "user_id"
        const val KEY_ROLE = "role"
        const val KEY_LOGIN_STATE = "login_state"
        const val KEY_API_TOKEN = "api_token"
    }
}