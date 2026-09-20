package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import java.time.LocalDate
import java.time.LocalTime

interface BookingRepository {
    /** Raw counts keyed by every status the prosumer has at least one booking in. */
    fun getStatusCounts(nic: String): Map<BookingStatus, Int>

    /** Soonest APPROVED booking strictly later than now. */
    fun getUpcomingBooking(nic: String): Booking?

    /** Count of this prosumer's APPROVED bookings strictly later than now. */
    fun getApprovedFutureCount(nic: String): Int

    fun getBookingById(bookingId: String): Booking?

    fun getBookingsByProsumer(nic: String): List<Booking>

    /** Display-ready, joined-with-station-name rows for the My Bookings screen. */
    fun getBookingListItems(nic: String): List<BookingListItem>

    /** Local-first: writes a Pending/PENDING_SYNC row immediately (client-generated id) and
     * queues the request for the backend - see LocalDbManager.createBookingLocally/SyncWorker.
     * Always succeeds locally; a definitive server rejection (e.g. slot no longer available) is
     * only discovered once synced and surfaces as SYNC_FAILED on the local row, not as an
     * AppResult.Failure here. */
    fun createBooking(
        prosumerNic: String,
        nodeId: String,
        slotId: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking>

    /** Local-first reschedule of the time window only (node/slot/energy amount stay fixed - the
     * backend's UpdateReservationRequest has no energyAmount field, matching its own deliberate
     * "update only reschedules" design). Re-verifies ownership/status/12-hour notice against the
     * local cache (the only synchronous check available now - see createBooking) before queuing. */
    fun updateBooking(
        bookingId: String,
        prosumerNic: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime
    ): AppResult<Booking>

    /** Local-first cancel: re-verifies ownership/status/12-hour notice against the local cache,
     * then queues the cancellation for the backend. */
    fun cancelBooking(bookingId: String, prosumerNic: String): AppResult<Unit>

    /** GRID_OPERATOR only. Local-first: queues an approve for the backend (Pending -> Confirmed).
     * No local `bookings` row is touched - operators don't cache other prosumers' reservations
     * (see file header on BookingRepositoryImpl) - and no QR transaction is generated here any
     * more; see TransactionRepository for the prosumer-initiated QR pass flow. */
    fun approveBooking(bookingId: String): AppResult<Unit>

    /** GRID_OPERATOR only. Local-first: queues a reject for the backend (Pending -> Rejected, a
     * terminal state distinct from a prosumer-initiated cancel). */
    fun rejectBooking(bookingId: String): AppResult<Unit>

    /** GRID_OPERATOR only. Cross-prosumer, every status - the read-only Bookings overview tab. */
    fun getAllBookingListItems(): List<BookingListItem>
}
