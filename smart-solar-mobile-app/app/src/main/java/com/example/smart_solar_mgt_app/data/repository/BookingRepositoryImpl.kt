package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class BookingRepositoryImpl(private val localDbManager: LocalDbManager) : BookingRepository {

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    override fun getStatusCounts(nic: String): Map<BookingStatus, Int> =
        localDbManager.getBookingStatusCounts(nic)

    override fun getUpcomingBooking(nic: String): Booking? {
        val nowDate = LocalDate.now().toString()
        val nowTime = LocalTime.now().format(timeFormatter)
        return localDbManager.getUpcomingBooking(nic, nowDate, nowTime)
    }

    override fun getBookingById(bookingId: String): Booking? = localDbManager.getBookingById(bookingId)

    override fun getBookingsByProsumer(nic: String): List<Booking> = localDbManager.getBookingsByProsumer(nic)
}
