package com.example.smart_solar_mgt_app.core.network

/**
 * Result of a POST /api/v1/auth/login or POST /api/v1/auth/prosumer/login call against the Web
 * Service, kept separate from the generic AppError vocabulary (core.common.Resource) since these
 * are specifically the error codes AuthEndpoints returns - see
 * smart-solar-mgt-api/Endpoints/AuthEndpoints.cs and smart-solar-mgt-api.http. Shared by both
 * endpoints even though each only ever returns a subset of RemoteLoginRejection - see
 * SecurityManagerImpl.loginRemoteStaffOrProsumerByEmail/loginRemoteProsumer.
 */
sealed class RemoteLoginOutcome {
    data class Success(val session: RemoteAuthSession) : RemoteLoginOutcome()
    data class Rejected(val reason: RemoteLoginRejection) : RemoteLoginOutcome()
    data class NetworkFailure(val message: String) : RemoteLoginOutcome()
}

enum class RemoteLoginRejection {
    INVALID_CREDENTIALS,
    PROFILE_INCOMPLETE,
    ACCOUNT_DEACTIVATED,
    PROSUMER_MOBILE_ONLY,
    // prosumer/login-only outcomes (see ProsumerLoginAsync)
    PENDING_APPROVAL,
    ACCOUNT_CREATION_DENIED,
    PASSWORD_NOT_SET,
    UNKNOWN
}

/** The bearer token pair + role from a successful LoginResponse (see Models/Dtos/LoginResponse.cs). */
data class RemoteAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val role: String
)
