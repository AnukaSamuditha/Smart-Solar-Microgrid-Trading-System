package com.example.smart_solar_mgt_app.domain.model

import com.example.smart_solar_mgt_app.core.common.SyncStatus

/** A backend write queued locally (see core/sync/SyncManager.kt) because it either happened
 * offline or hasn't been confirmed by the server yet. RESERVATION_APPROVE/RESERVATION_REJECT
 * have no local `bookings` row to reconcile (Grid Operators don't cache other prosumers'
 * reservations - see BookingRepositoryImpl.approveBooking/rejectBooking); entityRef for those is
 * just the backend's reservation id, used only to make the API call. Transaction generate/scan/
 * complete are deliberately NOT queueable here - see TransactionRepositoryImpl's file header for
 * why that stays online-only. */
enum class OutboxOperationType {
    PROSUMER_REGISTER,
    PROSUMER_APPROVE,
    PROSUMER_DENY,
    RESERVATION_CREATE,
    RESERVATION_UPDATE,
    RESERVATION_CANCEL,
    RESERVATION_APPROVE,
    RESERVATION_REJECT
}

data class OutboxOperation(
    val id: String,
    val operationType: OutboxOperationType,
    /** The entity this operation concerns - a NIC for PROSUMER_*, a `bookings.booking_id` (a
     * client-generated id until RESERVATION_CREATE syncs, the backend's real id thereafter) for
     * RESERVATION_*. Used to reconcile the local row on success. */
    val entityRef: String,
    /** JSON request body for the matching RemoteProsumerRepository call. */
    val payloadJson: String,
    /** PENDING_SYNC (not yet attempted, or attempted and failed transiently - retried) or
     * SYNC_FAILED (a definitive server rejection - kept for the user to see, never retried). */
    val status: SyncStatus,
    val attemptCount: Int,
    val lastError: String?,
    val createdAt: Long,
    val updatedAt: Long
)
