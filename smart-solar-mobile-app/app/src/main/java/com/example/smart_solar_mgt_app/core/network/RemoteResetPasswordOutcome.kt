package com.example.smart_solar_mgt_app.core.network

/**
 * Result of a POST /api/v1/auth/accept-invitation call - shared by the web invitation-acceptance
 * flow and this app's mobile Reset Password screen (see AuthEndpoints.AcceptInvitationAsync and
 * ResetPasswordActivity). No tokens come back - the prosumer logs in separately afterwards.
 */
sealed class RemoteResetPasswordOutcome {
    data object Success : RemoteResetPasswordOutcome()
    data class Rejected(val reason: RemoteResetPasswordRejection) : RemoteResetPasswordOutcome()
    data class NetworkFailure(val message: String) : RemoteResetPasswordOutcome()
}

enum class RemoteResetPasswordRejection {
    INVALID_CODE,
    EXPIRED,
    ALREADY_USED,
    UNKNOWN
}
