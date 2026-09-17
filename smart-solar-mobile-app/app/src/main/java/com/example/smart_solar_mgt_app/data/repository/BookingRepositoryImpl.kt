package com.example.smart_solar_mgt_app.data.repository

import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.db.LocalDbManager
import com.example.smart_solar_mgt_app.core.security.QrTokenService
import com.example.smart_solar_mgt_app.core.security.SecurityManager
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.util.DateFormats
import com.example.smart_solar_mgt_app.util.TransactionRules
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

class BookingRepositoryImpl(
    private val localDbManager: LocalDbManager,
    private val securityManager: SecurityManager,
    private val qrTokenService: QrTokenService
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

    override fun updateBooking(
        bookingId: String,
        prosumerNic: String,
        bookingDate: LocalDate,
        bookingTime: LocalTime,
        energyAmount: Double
    ): AppResult<Booking> = localDbManager.updateBooking(
        bookingId = bookingId,
        prosumerNic = prosumerNic,
        bookingDate = bookingDate.toString(),
        bookingTime = bookingTime.format(DateFormats.TIME_FORMATTER),
        energyAmount = energyAmount,
        now = System.currentTimeMillis()
    )

    override fun cancelBooking(bookingId: String, prosumerNic: String): AppResult<Unit> =
        localDbManager.cancelBooking(bookingId, prosumerNic, System.currentTimeMillis())

    override fun getAllPendingBookings(): List<Booking> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        return localDbManager.getBookingsByStatus(BookingStatus.PENDING)
    }

    override fun approveBooking(bookingId: String): AppResult<Booking> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        val transactionId = UUID.randomUUID().toString()
        val expiryMillis = TransactionRules.expiryMillis(System.currentTimeMillis())
        val qrToken = qrTokenService.sign(transactionId, bookingId, expiryMillis)
        return localDbManager.approveBooking(bookingId, transactionId, qrToken)
    }

    override fun rejectBooking(bookingId: String): AppResult<Unit> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        return localDbManager.rejectBooking(bookingId)
    }

    override fun getAllBookingListItems(): List<BookingListItem> {
        securityManager.requireRole(Role.GRID_OPERATOR)
        return localDbManager.getAllBookingListItems()
    }
}
