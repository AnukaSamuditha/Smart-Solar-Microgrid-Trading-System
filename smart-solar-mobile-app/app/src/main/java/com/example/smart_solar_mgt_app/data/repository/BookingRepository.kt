package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import java.time.LocalDate
import java.time.LocalTime

interface BookingRepository {
    /** Raw counts keyed by every status the prosumer has at least one booking in. */
    fun getStatusCounts(nic: String): Map<BookingStatus, Int>

    /** Soonest active (PENDING/CONFIRMED) booking at or after now. */
    fun getUpcomingBooking(nic: String): Booking?

    fun getBookingById(bookingId: String): Booking?

    fun getBookingsByProsumer(nic: String): List<Booking>

    /** Atomic: inserts the booking and consumes one station slot, or fails if the station isn't available. */
    fun createBooking(
        prosumerNic: String,
        stationId: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking>
}
