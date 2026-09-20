package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.network.RemoteReservation
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.util.DateFormats
import java.time.ZoneId

/**
 * The single place a RemoteReservation (backend wire vocabulary) is translated into this app's
 * local Booking domain model - shared by BookingRepositoryImpl (the online path) and SyncWorker
 * (reconciling a synced RESERVATION_CREATE's server-assigned id), so the status mapping in
 * particular can never drift between the two call sites. Free functions rather than private
 * members of either class for exactly that reason.
 */
internal fun RemoteReservation.toSyncedBooking(): Booking {
    val zoned = startTime.atZone(ZoneId.systemDefault())
    val now = System.currentTimeMillis()
    return Booking(
        bookingId = id,
        prosumerNic = prosumerNic,
        nodeId = nodeId,
        slotId = slotId,
        bookingDate = zoned.toLocalDate().toString(),
        bookingTime = zoned.toLocalTime().format(DateFormats.TIME_FORMATTER),
        energyAmount = energyAmount ?: 0.0,
        status = mapRemoteReservationStatus(status),
        syncStatus = SyncStatus.SYNCED,
        createdAt = now,
        updatedAt = now
    )
}

// backend ReservationStatus -> local BookingStatus. EXPIRED has no backend equivalent and is
// never mapped to (it stays aspirational, matching this app's existing behavior - see BookingStatus.kt)
internal fun mapRemoteReservationStatus(remoteStatus: String): BookingStatus = when (remoteStatus) {
    "Pending" -> BookingStatus.PENDING
    "Confirmed" -> BookingStatus.APPROVED
    "Rejected" -> BookingStatus.REJECTED
    "Cancelled" -> BookingStatus.CANCELLED
    "Completed" -> BookingStatus.COMPLETED
    else -> BookingStatus.PENDING
}
