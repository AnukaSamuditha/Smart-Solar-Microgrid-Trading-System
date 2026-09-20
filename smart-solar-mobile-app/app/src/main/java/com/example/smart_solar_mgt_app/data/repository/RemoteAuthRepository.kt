package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.network.RemoteLoginOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteResetPasswordOutcome

/**
 * Auth calls against the real Web Service (smart-solar-mgt-api), as opposed to AuthRepository,
 * which owns the local SQLite `users` table.
 */
interface RemoteAuthRepository {
    /** Staff (Backoffice/Grid Operator) login by email - POST /api/v1/auth/login. */
    fun login(email: String, password: String): RemoteLoginOutcome

    /** Prosumer login by NIC or email - POST /api/v1/auth/prosumer/login. */
    fun prosumerLogin(nicOrEmail: String, password: String): RemoteLoginOutcome

    /**
     * Consumes an invitation/reset code and sets a new password - POST
     * /api/v1/auth/accept-invitation. Shared by the staff/prosumer invitation flow and this
     * app's mobile Reset Password screen; the caller doesn't need to know or care which.
     */
    fun resetPassword(code: String, newPassword: String): RemoteResetPasswordOutcome

    /**
     * Rotates the stored refresh token for a new access/refresh pair - POST /api/v1/auth/refresh
     * - and updates SecureSessionStore on success. Used to recover from an expired access token
     * on an authenticated call (see RemoteProsumerRepositoryImpl) without forcing a fresh login,
     * mirroring the web frontend's silent-refresh-and-retry interceptor
     * (smart-solar-mgt-fe/providers/api-client.ts). Returns whether it succeeded; false means the
     * refresh token itself is missing/expired/revoked and only a real login can recover.
     */
    fun refreshSession(): Boolean
}
