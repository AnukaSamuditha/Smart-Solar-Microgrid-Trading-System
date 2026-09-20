package com.example.smart_solar_mgt_app.core.network

import java.time.Instant

/** Display-ready details returned by POST /api/v1/transactions/scan and
 * PATCH /api/v1/transactions/{id}/complete (see TransactionVerificationResponse). */
data class RemoteTransactionVerification(
    val transactionId: String,
    val reservationId: String,
    val prosumerNic: String,
    val nodeName: String?,
    val startTime: Instant?,
    val endTime: Instant?,
    val energyAmount: Double?,
    val status: String
)

/** Result of POST /api/v1/transactions/generate - a prosumer requesting a QR Energy Transfer
 * Pass for one of their own Confirmed reservations. */
sealed class RemoteTransactionGenerateOutcome {
    data class Success(val transactionId: String, val token: String, val expiresAt: Instant) : RemoteTransactionGenerateOutcome()
    data class Rejected(val reason: RemoteTransactionGenerateRejection) : RemoteTransactionGenerateOutcome()
    data class NetworkFailure(val message: String) : RemoteTransactionGenerateOutcome()
}

enum class RemoteTransactionGenerateRejection {
    RESERVATION_NOT_FOUND,
    RESERVATION_NOT_CONFIRMED,
    RESERVATION_WINDOW_ELAPSED,
    UNKNOWN
}

/** Result of POST /api/v1/transactions/scan (Grid-Operator-only). */
sealed class RemoteTransactionScanOutcome {
    data class Success(val verification: RemoteTransactionVerification) : RemoteTransactionScanOutcome()
    data class Rejected(val reason: RemoteTransactionScanRejection) : RemoteTransactionScanOutcome()
    data class NetworkFailure(val message: String) : RemoteTransactionScanOutcome()
}

enum class RemoteTransactionScanRejection {
    NOT_FOUND,
    ALREADY_USED,
    EXPIRED,
    RESERVATION_NO_LONGER_CONFIRMED,
    UNKNOWN
}

/** Result of PATCH /api/v1/transactions/{id}/complete (Grid-Operator-only). */
sealed class RemoteTransactionCompleteOutcome {
    data class Success(val verification: RemoteTransactionVerification) : RemoteTransactionCompleteOutcome()
    data class Rejected(val reason: RemoteTransactionCompleteRejection) : RemoteTransactionCompleteOutcome()
    data class NetworkFailure(val message: String) : RemoteTransactionCompleteOutcome()
}

enum class RemoteTransactionCompleteRejection {
    NOT_FOUND,
    NOT_SCANNED,
    UNKNOWN
}
