package com.example.smart_solar_mgt_app.core.network

/** Result of a POST /api/v1/prosumers/register call (public, mobile self-registration). */
sealed class RemoteProsumerRegisterOutcome {
    data object Success : RemoteProsumerRegisterOutcome()
    data class Rejected(val reason: RemoteProsumerRegisterRejection) : RemoteProsumerRegisterOutcome()
    data class NetworkFailure(val message: String) : RemoteProsumerRegisterOutcome()
}

enum class RemoteProsumerRegisterRejection {
    INVALID_NIC,
    INVALID_EMAIL,
    NIC_ALREADY_IN_USE,
    EMAIL_ALREADY_IN_USE,
    UNKNOWN
}

/** Result of GET /api/v1/prosumers?status=PendingApproval, used by the Grid Operator review queue. */
sealed class RemoteProsumerListOutcome {
    data class Success(val items: List<PendingProsumerRequest>) : RemoteProsumerListOutcome()
    data class NetworkFailure(val message: String) : RemoteProsumerListOutcome()
}

/** A self-registered prosumer's submitted details, as shown to a Backoffice/Grid Operator reviewer. */
data class PendingProsumerRequest(
    val nic: String,
    val fullName: String?,
    val email: String,
    val phone: String?,
    val address: String?
)

/** Result of PATCH /api/v1/prosumers/{nic}/approve or /deny. */
sealed class RemoteProsumerReviewOutcome {
    data object Success : RemoteProsumerReviewOutcome()
    data class Rejected(val reason: RemoteProsumerReviewRejection) : RemoteProsumerReviewOutcome()
    data class NetworkFailure(val message: String) : RemoteProsumerReviewOutcome()
}

enum class RemoteProsumerReviewRejection {
    NOT_FOUND,
    NOT_PENDING_APPROVAL,
    UNAUTHORIZED,
    UNKNOWN
}

/** Result of GET /api/v1/prosumers/me - a logged-in prosumer's own canonical profile. */
sealed class RemoteProsumerProfileOutcome {
    data class Success(val profile: ProsumerProfile) : RemoteProsumerProfileOutcome()
    data class NetworkFailure(val message: String) : RemoteProsumerProfileOutcome()
}

/** The server's canonical record for the currently logged-in prosumer - used right after a
 * remote login to learn the real NIC (session.userId must be the NIC, not whatever the prosumer
 * typed to log in - see SecurityManagerImpl.loginRemoteProsumer) and to seed the local profile
 * cache Home/Profile read from (AuthRepository.upsertProsumerProfileCache). */
data class ProsumerProfile(
    val nic: String,
    val fullName: String?,
    val email: String,
    val phone: String?,
    val address: String?
)
