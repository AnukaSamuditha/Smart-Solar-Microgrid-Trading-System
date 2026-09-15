package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.util.DateFormats
import java.time.LocalDate
import java.time.LocalTime

class BookingRepositoryImpl(private val localDbManager: LocalDbManager) : BookingRepository {

    override fun getStatusCounts(nic: String): Map<BookingStatus, Int> =
        localDbManager.getBookingStatusCounts(nic)

    override fun getUpcomingBooking(nic: String): Booking? {
        val nowDate = LocalDate.now().toString()
        val nowTime = LocalTime.now().format(DateFormats.TIME_FORMATTER)
        return localDbManager.getUpcomingBooking(nic, nowDate, nowTime)
    }

    override fun getBookingById(bookingId: String): Booking? = localDbManager.getBookingById(bookingId)

    override fun getBookingsByProsumer(nic: String): List<Booking> = localDbManager.getBookingsByProsumer(nic)

    override fun createBooking(
        prosumerNic: String,
        stationId: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking> = localDbManager.createBooking(
        prosumerNic = prosumerNic,
        stationId = stationId,
        bookingDate = bookingDate.toString(),
        bookingTime = bookingTime.format(DateFormats.TIME_FORMATTER),
        energyAmount = energyAmount,
        now = System.currentTimeMillis()
    )
}
