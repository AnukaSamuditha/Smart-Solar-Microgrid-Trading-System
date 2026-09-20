package com.example.smart_solar_mgt_app.core.network

import java.time.Instant

/** The backend's canonical shape for a reservation (see ReservationResponse.FromEntity), as
 * returned by POST /api/v1/reservations/mine, PATCH /api/v1/reservations/mine/{id}, and
 * GET /api/v1/reservations?status=Pending (the staff pending-approval queue). prosumerNic/
 * prosumerFullName are redundant for the prosumer's own self-service calls (always their own
 * identity) but essential for the cross-prosumer staff list. */
data class RemoteReservation(
    val id: String,
    val prosumerNic: String,
    val prosumerFullName: String?,
    val nodeId: String,
    val nodeName: String?,
    val slotId: String,
    val startTime: Instant,
    val endTime: Instant,
    val energyAmount: Double?,
    val status: String,
    val rejectionReason: String?
)

/** Result of GET /api/v1/reservations?status=Pending (Backoffice/Grid Operator) - the
 * cross-prosumer pending-approval queue. */
sealed class RemoteReservationListOutcome {
    data class Success(val reservations: List<RemoteReservation>) : RemoteReservationListOutcome()
    data class NetworkFailure(val message: String) : RemoteReservationListOutcome()
}

/** Result of POST /api/v1/reservations/mine - a prosumer's self-service reservation request. */
sealed class RemoteReservationRequestOutcome {
    data class Success(val reservation: RemoteReservation) : RemoteReservationRequestOutcome()
    data class Rejected(val reason: RemoteReservationRequestRejection) : RemoteReservationRequestOutcome()
    data class NetworkFailure(val message: String) : RemoteReservationRequestOutcome()
}

enum class RemoteReservationRequestRejection {
    NODE_NOT_FOUND,
    SLOT_NOT_FOUND,
    PROSUMER_DEACTIVATED,
    NODE_DEACTIVATED,
    SLOT_NOT_AVAILABLE,
    /** Any of the 400 time-window validation codes - the client-side BookingValidator already
     * prevents these in practice, so they're collapsed into one generic case rather than mapped
     * one-for-one. */
    INVALID_WINDOW,
    UNKNOWN
}

/** Result of PATCH /api/v1/reservations/mine/{id} - reschedules the time window only. */
sealed class RemoteReservationUpdateOutcome {
    data class Success(val reservation: RemoteReservation) : RemoteReservationUpdateOutcome()
    data class Rejected(val reason: RemoteReservationModifyRejection) : RemoteReservationUpdateOutcome()
    data class NetworkFailure(val message: String) : RemoteReservationUpdateOutcome()
}

/** Result of PATCH /api/v1/reservations/mine/{id}/cancel (no response body on success). */
sealed class RemoteReservationActionOutcome {
    data object Success : RemoteReservationActionOutcome()
    data class Rejected(val reason: RemoteReservationModifyRejection) : RemoteReservationActionOutcome()
    data class NetworkFailure(val message: String) : RemoteReservationActionOutcome()
}

enum class RemoteReservationModifyRejection {
    NOT_FOUND,
    ALREADY_CANCELLED,
    ALREADY_STARTED,
    INSUFFICIENT_NOTICE,
    UNKNOWN
}

/** Result of PATCH /api/v1/reservations/{id}/approve or /reject (staff-only). */
sealed class RemoteReservationReviewOutcome {
    data object Success : RemoteReservationReviewOutcome()
    data class Rejected(val reason: RemoteReservationReviewRejection) : RemoteReservationReviewOutcome()
    data class NetworkFailure(val message: String) : RemoteReservationReviewOutcome()
}

enum class RemoteReservationReviewRejection {
    NOT_FOUND,
    NOT_PENDING,
    SLOT_NO_LONGER_AVAILABLE,
    UNKNOWN
}
