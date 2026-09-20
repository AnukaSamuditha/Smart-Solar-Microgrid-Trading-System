package com.example.smart_solar_mgt_app.core.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerRegisterOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerRegisterRejection
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerReviewOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteProsumerReviewRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationActionOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationModifyRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationRequestOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationRequestRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationReviewOutcome
import com.example.smart_solar_mgt_app.core.network.RemoteReservationReviewRejection
import com.example.smart_solar_mgt_app.core.network.RemoteReservationUpdateOutcome
import com.example.smart_solar_mgt_app.core.network.optNullableString
import com.example.smart_solar_mgt_app.data.repository.RemoteProsumerRepository
import com.example.smart_solar_mgt_app.data.repository.RemoteReservationRepository
import com.example.smart_solar_mgt_app.data.repository.toSyncedBooking
import com.example.smart_solar_mgt_app.di.ServiceLocator
import com.example.smart_solar_mgt_app.domain.model.OutboxOperation
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Drains core/db/DatabaseContract.SyncOutbox against the real backend, one WorkManager run at a
 * time. Reaches into ServiceLocator directly rather than taking constructor dependencies - the
 * default WorkerFactory instantiates Workers via reflection with just (context, params), and
 * this project has no DI framework to hook a custom WorkerFactory into (see ServiceLocator.kt).
 *
 * Transaction generate/scan/complete are deliberately never queued here - see
 * TransactionRepositoryImpl's file header and OutboxOperationType's doc comment for why those
 * stay online-only.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val localDbManager = ServiceLocator.localDbManager
        val remoteProsumerRepository = ServiceLocator.remoteProsumerRepository
        val remoteReservationRepository = ServiceLocator.remoteReservationRepository

        var anyTransientFailure = false

        for (operation in localDbManager.getPendingSyncOutboxOperations()) {
            when (val outcome = attempt(operation, remoteProsumerRepository, remoteReservationRepository)) {
                SyncOutcome.Success -> localDbManager.deleteOutboxOperation(operation.id)
                is SyncOutcome.Rejected -> {
                    localDbManager.markOutboxOperationRejected(operation.id, outcome.message)
                    if (isReservationEntityOperation(operation.operationType)) {
                        localDbManager.markBookingSyncFailed(operation.entityRef)
                    }
                }
                is SyncOutcome.Transient -> {
                    localDbManager.recordOutboxAttemptFailure(operation.id, outcome.message)
                    anyTransientFailure = true
                }
            }
        }

        // WorkManager's own backoff policy governs the retry delay - we just report whether one is needed.
        if (anyTransientFailure) Result.retry() else Result.success()
    }

    // RESERVATION_APPROVE/REJECT have no local `bookings` row to mark (see OutboxOperationType) -
    // only CREATE/UPDATE/CANCEL, whose entityRef is always a bookings.booking_id
    private fun isReservationEntityOperation(type: OutboxOperationType): Boolean = when (type) {
        OutboxOperationType.RESERVATION_CREATE, OutboxOperationType.RESERVATION_UPDATE, OutboxOperationType.RESERVATION_CANCEL -> true
        else -> false
    }

    private fun attempt(
        operation: OutboxOperation,
        prosumerRepo: RemoteProsumerRepository,
        reservationRepo: RemoteReservationRepository
    ): SyncOutcome =
        try {
            when (operation.operationType) {
                OutboxOperationType.PROSUMER_REGISTER -> attemptRegister(operation, prosumerRepo)
                OutboxOperationType.PROSUMER_APPROVE -> attemptProsumerReview(prosumerRepo.approve(operation.entityRef))
                OutboxOperationType.PROSUMER_DENY -> attemptProsumerDeny(operation, prosumerRepo)
                OutboxOperationType.RESERVATION_CREATE -> attemptReservationCreate(operation, reservationRepo)
                OutboxOperationType.RESERVATION_UPDATE -> attemptReservationUpdate(operation, reservationRepo)
                OutboxOperationType.RESERVATION_CANCEL -> attemptReservationCancel(operation, reservationRepo)
                OutboxOperationType.RESERVATION_APPROVE -> attemptReservationReview(reservationRepo.approve(operation.entityRef))
                OutboxOperationType.RESERVATION_REJECT -> attemptReservationReview(reservationRepo.reject(operation.entityRef, reason = null))
            }
        } catch (e: Exception) {
            // a malformed payload (shouldn't happen - we wrote it ourselves) is not worth
            // retrying forever, but is also not a real server rejection
            SyncOutcome.Rejected(e.message ?: "Malformed outbox entry")
        }

    private fun attemptRegister(operation: OutboxOperation, repo: RemoteProsumerRepository): SyncOutcome {
        val json = JSONObject(operation.payloadJson)
        val outcome = repo.register(
            nic = json.getString("nic"),
            email = json.getString("email"),
            fullName = json.getString("fullName"),
            phone = json.optNullableString("phone"),
            address = json.optNullableString("address")
        )
        return when (outcome) {
            RemoteProsumerRegisterOutcome.Success -> SyncOutcome.Success
            is RemoteProsumerRegisterOutcome.Rejected -> SyncOutcome.Rejected(messageFor(outcome.reason))
            is RemoteProsumerRegisterOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
        }
    }

    private fun attemptProsumerDeny(operation: OutboxOperation, repo: RemoteProsumerRepository): SyncOutcome {
        val reason = JSONObject(operation.payloadJson).optNullableString("reason")
        return attemptProsumerReview(repo.deny(operation.entityRef, reason))
    }

    private fun attemptProsumerReview(outcome: RemoteProsumerReviewOutcome): SyncOutcome = when (outcome) {
        RemoteProsumerReviewOutcome.Success -> SyncOutcome.Success
        is RemoteProsumerReviewOutcome.Rejected -> when (outcome.reason) {
            // RemoteProsumerRepositoryImpl already tried one token refresh+retry before
            // surfacing this - a residual 401 means the refresh token itself is expired/revoked,
            // not that the approve/deny itself is invalid. Left PENDING_SYNC (not SYNC_FAILED) so
            // it resolves on its own the next time the Grid Operator logs in and this worker runs
            // again, rather than being abandoned.
            RemoteProsumerReviewRejection.UNAUTHORIZED -> SyncOutcome.Transient("Session expired - log in again to sync this")
            else -> SyncOutcome.Rejected(outcome.reason.name)
        }
        is RemoteProsumerReviewOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
    }

    private fun attemptReservationCreate(operation: OutboxOperation, repo: RemoteReservationRepository): SyncOutcome {
        val json = JSONObject(operation.payloadJson)
        val outcome = repo.request(
            nodeId = json.getString("nodeId"),
            slotId = json.getString("slotId"),
            startTime = Instant.parse(json.getString("startTime")),
            endTime = Instant.parse(json.getString("endTime")),
            energyAmount = if (json.isNull("energyAmount") || !json.has("energyAmount")) null else json.getDouble("energyAmount")
        )
        return when (outcome) {
            is RemoteReservationRequestOutcome.Success -> {
                // reconciles the client-generated temp id (operation.entityRef) to the backend's
                // real one - see LocalDbManager.reconcileCreatedBooking
                ServiceLocator.localDbManager.reconcileCreatedBooking(operation.entityRef, outcome.reservation.toSyncedBooking())
                SyncOutcome.Success
            }
            is RemoteReservationRequestOutcome.Rejected -> SyncOutcome.Rejected(messageFor(outcome.reason))
            is RemoteReservationRequestOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
        }
    }

    private fun attemptReservationUpdate(operation: OutboxOperation, repo: RemoteReservationRepository): SyncOutcome {
        val json = JSONObject(operation.payloadJson)
        val outcome = repo.update(
            reservationId = operation.entityRef,
            startTime = Instant.parse(json.getString("startTime")),
            endTime = Instant.parse(json.getString("endTime"))
        )
        return when (outcome) {
            is RemoteReservationUpdateOutcome.Success -> {
                ServiceLocator.localDbManager.markBookingSynced(operation.entityRef)
                SyncOutcome.Success
            }
            is RemoteReservationUpdateOutcome.Rejected -> SyncOutcome.Rejected(messageFor(outcome.reason))
            is RemoteReservationUpdateOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
        }
    }

    private fun attemptReservationCancel(operation: OutboxOperation, repo: RemoteReservationRepository): SyncOutcome =
        when (val outcome = repo.cancel(operation.entityRef)) {
            RemoteReservationActionOutcome.Success -> {
                ServiceLocator.localDbManager.markBookingSynced(operation.entityRef)
                SyncOutcome.Success
            }
            is RemoteReservationActionOutcome.Rejected -> SyncOutcome.Rejected(messageFor(outcome.reason))
            is RemoteReservationActionOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
        }

    private fun attemptReservationReview(outcome: RemoteReservationReviewOutcome): SyncOutcome = when (outcome) {
        RemoteReservationReviewOutcome.Success -> SyncOutcome.Success
        is RemoteReservationReviewOutcome.Rejected -> SyncOutcome.Rejected(messageFor(outcome.reason))
        is RemoteReservationReviewOutcome.NetworkFailure -> SyncOutcome.Transient(outcome.message)
    }

    private fun messageFor(reason: RemoteProsumerRegisterRejection): String = when (reason) {
        RemoteProsumerRegisterRejection.INVALID_NIC -> "The NIC on this request isn't valid."
        RemoteProsumerRegisterRejection.INVALID_EMAIL -> "The email on this request isn't valid."
        RemoteProsumerRegisterRejection.NIC_ALREADY_IN_USE -> "This NIC is already registered."
        RemoteProsumerRegisterRejection.EMAIL_ALREADY_IN_USE -> "This email is already registered."
        RemoteProsumerRegisterRejection.UNKNOWN -> "The server rejected this request."
    }

    private fun messageFor(reason: RemoteReservationRequestRejection): String = when (reason) {
        RemoteReservationRequestRejection.NODE_NOT_FOUND -> "The selected node no longer exists."
        RemoteReservationRequestRejection.SLOT_NOT_FOUND -> "The selected slot no longer exists."
        RemoteReservationRequestRejection.PROSUMER_DEACTIVATED -> "Your account is deactivated."
        RemoteReservationRequestRejection.NODE_DEACTIVATED -> "This node is no longer active."
        RemoteReservationRequestRejection.SLOT_NOT_AVAILABLE -> "This slot was taken before this request could be sent."
        RemoteReservationRequestRejection.INVALID_WINDOW -> "This reservation's time window is no longer valid."
        RemoteReservationRequestRejection.UNKNOWN -> "The server rejected this request."
    }

    private fun messageFor(reason: RemoteReservationModifyRejection): String = when (reason) {
        RemoteReservationModifyRejection.NOT_FOUND -> "This reservation no longer exists."
        RemoteReservationModifyRejection.ALREADY_CANCELLED -> "This reservation was already cancelled."
        RemoteReservationModifyRejection.ALREADY_STARTED -> "This reservation has already started."
        RemoteReservationModifyRejection.INSUFFICIENT_NOTICE -> "Less than 12 hours' notice remained by the time this synced."
        RemoteReservationModifyRejection.UNKNOWN -> "The server rejected this change."
    }

    private fun messageFor(reason: RemoteReservationReviewRejection): String = when (reason) {
        RemoteReservationReviewRejection.NOT_FOUND -> "This reservation no longer exists."
        RemoteReservationReviewRejection.NOT_PENDING -> "This reservation is no longer pending."
        RemoteReservationReviewRejection.SLOT_NO_LONGER_AVAILABLE -> "This slot was claimed by another reservation first."
        RemoteReservationReviewRejection.UNKNOWN -> "The server rejected this action."
    }

    private sealed class SyncOutcome {
        data object Success : SyncOutcome()
        data class Rejected(val message: String) : SyncOutcome()
        data class Transient(val message: String) : SyncOutcome()
    }
}
