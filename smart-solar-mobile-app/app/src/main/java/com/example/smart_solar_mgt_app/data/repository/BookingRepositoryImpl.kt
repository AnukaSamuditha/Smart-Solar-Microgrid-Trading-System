package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.core.sync.SyncManager
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.util.BookingTimeRules
import com.example.smart_solar_mgt_app.util.DateFormats
import com.example.smart_solar_mgt_app.util.ReservationRules
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONObject

/**
 * Translation boundary between this app's "Booking" domain vocabulary (kept as-is across every
 * UI/ViewModel file, unchanged from the fully-local-first design) and the backend's "Reservation"
 * wire vocabulary (RemoteReservationRepository/RemoteReservation - see ReservationMapping.kt for
 * the shared translation) - the same pattern RemoteProsumerRepository already uses (ProsumerProfile
 * rather than the local User model).
 *
 * Local-first, offline-safe: create/update/cancel write a local PENDING_SYNC row and queue a
 * matching outbox entry (see LocalDbManager's RESERVATION_* methods), then return success
 * immediately regardless of connectivity - SyncWorker is what actually calls the backend, exactly
 * like RegisterViewModel's registerProsumerLocally precedent. A definitive server rejection
 * (e.g. SlotNotAvailable) is only discovered asynchronously and surfaces as SYNC_FAILED, not as
 * an AppResult.Failure here - the client-side checks in this class (ownership, status, 12-hour
 * notice) are the only synchronous validation available now that the server round-trip is async.
 *
 * approveBooking/rejectBooking (Grid Operator only) are outbox-only with no local `bookings` row
 * at all - operators don't cache other prosumers' reservations (Phase 7's pending queue is always
 * fetched fresh from the backend), so there's nothing to write-through-cache; only the outbox
 * entry (entityRef = the backend's real reservation id, already known from that live list).
 */
class BookingRepositoryImpl(
    private val localDbManager: LocalDbManager,
    private val securityManager: SecurityManager,
    private val syncManager: SyncManager
) : BookingRepository {

    override fun getStatusCounts(nic: String): Map<BookingStatus, Int> =
        localDbManager.getBookingStatusCounts(nic)

    override fun getUpcomingBooking(nic: String): Booking? =
        localDbManager.getUpcomingBooking(nic, DateFormats.nowDateString(), DateFormats.nowTimeString())

    override fun getApprovedFutureCount(nic: String): Int =
        localDbManager.getApprovedFutureCount(nic, DateFormats.nowDateString(), DateFormats.nowTimeString())

    override fun getBookingById(bookingId: String): Booking? = localDbManager.getBookingById(bookingId)

    override fun getBookingsByProsumer(nic: String): List<Booking> = localDbManager.getBookingsByProsumer(nic)

    override fun getBookingListItems(nic: String): List<BookingListItem> = localDbManager.getBookingListItems(nic)

    override fun createBooking(
        prosumerNic: String,
        nodeId: String,
        slotId: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking> {
        val startInstant = toUtcInstant(bookingDate, bookingTime)
        val endInstant = startInstant.plus(ReservationRules.SESSION_DURATION)
        val payloadJson = JSONObject()
            .put("nodeId", nodeId)
            .put("slotId", slotId)
            .put("startTime", startInstant.toString())
            .put("endTime", endInstant.toString())
            .put("energyAmount", energyAmount)
            .toString()

        val now = System.currentTimeMillis()
        val booking = Booking(
            bookingId = UUID.randomUUID().toString(),
            prosumerNic = prosumerNic,
            nodeId = nodeId,
            slotId = slotId,
            bookingDate = bookingDate.toString(),
            bookingTime = bookingTime.format(DateFormats.TIME_FORMATTER),
            energyAmount = energyAmount,
            status = BookingStatus.PENDING,
            syncStatus = SyncStatus.PENDING_SYNC,
            createdAt = now,
            updatedAt = now
        )
        localDbManager.createBookingLocally(booking, payloadJson)
        syncManager.scheduleImmediateSync()
        return AppResult.Success(booking)
    }

    override fun updateBooking(
        bookingId: String,
        prosumerNic: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime
    ): AppResult<Booking> {
        val local = localDbManager.getBookingById(bookingId) ?: return AppResult.Failure(AppError.NotFound)
        if (local.prosumerNic != prosumerNic) return AppResult.Failure(AppError.Unauthorized)
        if (local.status != BookingStatus.PENDING && local.status != BookingStatus.APPROVED) {
            return AppResult.Failure(AppError.InvalidStatusTransition)
        }
        if (!canModifyOrCancel(local)) return AppResult.Failure(AppError.TooLateToModify)
        if (hasOutstandingCreate(bookingId)) {
            return AppResult.Failure(AppError.Unknown("This reservation hasn't finished syncing yet - try again shortly."))
        }

        val startInstant = toUtcInstant(bookingDate, bookingTime)
        val endInstant = startInstant.plus(ReservationRules.SESSION_DURATION)
        val payloadJson = JSONObject()
            .put("startTime", startInstant.toString())
            .put("endTime", endInstant.toString())
            .toString()

        val newBookingDate = bookingDate.toString()
        val newBookingTime = bookingTime.format(DateFormats.TIME_FORMATTER)
        localDbManager.updateBookingLocally(bookingId, newBookingDate, newBookingTime, payloadJson)
        syncManager.scheduleImmediateSync()

        val updated = localDbManager.getBookingById(bookingId) ?: return AppResult.Failure(AppError.NotFound)
        return AppResult.Success(updated)
    }

    override fun cancelBooking(bookingId: String, prosumerNic: String): AppResult<Unit> {
        val local = localDbManager.getBookingById(bookingId) ?: return AppResult.Failure(AppError.NotFound)
        if (local.prosumerNic != prosumerNic) return AppResult.Failure(AppError.Unauthorized)
        if (local.status != BookingStatus.PENDING && local.status != BookingStatus.APPROVED) {
            return AppResult.Failure(AppError.InvalidStatusTransition)
        }
        if (!canModifyOrCancel(local)) return AppResult.Failure(AppError.TooLateToModify)
        if (hasOutstandingCreate(bookingId)) {
            return AppResult.Failure(AppError.Unknown("This reservation hasn't finished syncing yet - try again shortly."))
        }

        localDbManager.cancelBookingLocally(bookingId)
        syncManager.scheduleImmediateSync()
        return AppResult.Success(Unit)
    }

    override fun approveBooking(bookingId: String): AppResult<Unit> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        localDbManager.enqueueOutboxOperation(OutboxOperationType.RESERVATION_APPROVE, bookingId, "{}")
        syncManager.scheduleImmediateSync()
        return AppResult.Success(Unit)
    }

    override fun rejectBooking(bookingId: String): AppResult<Unit> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        localDbManager.enqueueOutboxOperation(OutboxOperationType.RESERVATION_REJECT, bookingId, "{}")
        syncManager.scheduleImmediateSync()
        return AppResult.Success(Unit)
    }

    override fun getAllBookingListItems(): List<BookingListItem> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        return localDbManager.getAllBookingListItems()
    }

    // combines the form's separate date+time fields using the device's local timezone, matching
    // what the prosumer actually picked, then converts to the UTC instant the backend expects
    private fun toUtcInstant(date: LocalDate, time: LocalTime) =
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant()

    // a still-outstanding RESERVATION_CREATE means this reservation doesn't exist on the backend
    // yet - queuing an update/cancel against it now would risk the two operations arriving out of
    // order (or the update/cancel targeting an id the server has never heard of), so this is
    // refused with a clear "try again shortly" rather than silently queued
    private fun hasOutstandingCreate(bookingId: String): Boolean =
        localDbManager.getOutboxOperationForEntity(OutboxOperationType.RESERVATION_CREATE, bookingId) != null

    // client-side 12-hour notice re-check - the only synchronous enforcement available now that
    // the server round-trip is async (see file header); UI-level gating (BookingDetailFragment)
    // only decides whether to show the button, real time can elapse before this actually runs
    private fun canModifyOrCancel(booking: Booking): Boolean = BookingTimeRules.canModifyOrCancel(
        booking.status,
        LocalDate.parse(booking.bookingDate),
        LocalTime.parse(booking.bookingTime)
    )
}
