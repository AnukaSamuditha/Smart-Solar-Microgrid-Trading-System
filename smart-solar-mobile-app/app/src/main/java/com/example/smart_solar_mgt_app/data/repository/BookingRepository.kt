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

    /** Atomic: inserts the booking and consumes one station slot, or fails if the station isn't available. */
    fun createBooking(
        prosumerNic: String,
        stationId: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking>

    /** Atomic: re-verifies ownership/status/12-hour notice before applying the change. */
    fun updateBooking(
        bookingId: String,
        prosumerNic: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking>

    /** Atomic: re-verifies ownership/status/12-hour notice, then cancels and restores the station slot. */
    fun cancelBooking(bookingId: String, prosumerNic: String): AppResult<Unit>

    /**
     * Cross-prosumer - requires GRID_OPERATOR. Not a true reactive stream (no Flow in this
     * codebase yet); callers re-fetch on resume/refresh, same as every other list screen.
     */
    fun getAllPendingBookings(): List<Booking>

    /** GRID_OPERATOR only. PENDING -> APPROVED, and generates the QR transaction (Energy Transfer Pass). */
    fun approveBooking(bookingId: String): AppResult<Booking>

    /** GRID_OPERATOR only. PENDING -> CANCELLED (same terminal state a prosumer-initiated cancel uses), restores the station slot. */
    fun rejectBooking(bookingId: String): AppResult<Unit>

    /** GRID_OPERATOR only. Cross-prosumer, every status - the read-only Bookings overview tab. */
    fun getAllBookingListItems(): List<BookingListItem>
}
