package com.example.smart_solar_mgt_app.core.db

import android.content.Context
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.db.dao.BookingDao
import com.example.smart_solar_mgt_app.core.db.dao.SessionDao
import com.example.smart_solar_mgt_app.core.db.dao.StationDao
import com.example.smart_solar_mgt_app.core.db.dao.TransactionDao
import com.example.smart_solar_mgt_app.core.db.dao.UserDao
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.domain.model.StationStatus
import com.example.smart_solar_mgt_app.domain.model.Transaction
import com.example.smart_solar_mgt_app.domain.model.TransactionStatus
import com.example.smart_solar_mgt_app.domain.model.User
import java.util.UUID

/**
 * Facade over the local SQLite database. This is the ONLY class Repositories are allowed to
 * depend on for persistence - it owns cursor<->model mapping and wraps multi-table writes in a
 * single transaction. No Cursor/SQLiteDatabase type ever crosses this boundary.
 */
class LocalDbManager(context: Context) {

    private val helper = DatabaseHelper(context)

    private val userDao = UserDao()
    private val stationDao = StationDao()
    private val bookingDao = BookingDao()
    private val transactionDao = TransactionDao()
    private val sessionDao = SessionDao()

    // ---- Users ----

    fun insertUser(user: User) = userDao.insert(helper.writableDatabase, user)
    fun updateUser(user: User) = userDao.update(helper.writableDatabase, user)
    fun updateAccountStatus(nic: String, status: AccountStatus) = userDao.updateAccountStatus(helper.writableDatabase, nic, status)
    fun getUserByNic(nic: String): User? = userDao.getByNic(helper.readableDatabase, nic)
    fun getUserByEmail(email: String): User? = userDao.getByEmail(helper.readableDatabase, email)
    fun getUsersByRole(role: Role): List<User> = userDao.getByRole(helper.readableDatabase, role)

    // ---- Stations ----

    fun insertStation(station: SolarStation) = stationDao.insert(helper.writableDatabase, station)
    fun updateStation(station: SolarStation) = stationDao.update(helper.writableDatabase, station)
    fun getStationById(stationId: String): SolarStation? = stationDao.getById(helper.readableDatabase, stationId)
    fun getAllStations(): List<SolarStation> = stationDao.getAll(helper.readableDatabase)
    fun getAvailableStations(): List<SolarStation> = stationDao.getAvailable(helper.readableDatabase)
    fun updateStationStatus(stationId: String, status: StationStatus) = stationDao.updateStatus(helper.writableDatabase, stationId, status)

    // ---- Bookings (read/simple write) ----

    fun getBookingById(bookingId: String): Booking? = bookingDao.getById(helper.readableDatabase, bookingId)
    fun getBookingsByProsumer(nic: String): List<Booking> = bookingDao.getByProsumer(helper.readableDatabase, nic)
    fun getBookingsByStation(stationId: String): List<Booking> = bookingDao.getByStation(helper.readableDatabase, stationId)
    fun getBookingsByStatus(status: BookingStatus): List<Booking> = bookingDao.getByStatus(helper.readableDatabase, status)
    fun getPendingSyncBookings(): List<Booking> = bookingDao.getPendingSync(helper.readableDatabase)
    fun getBookingStatusCounts(nic: String): Map<BookingStatus, Int> = bookingDao.getStatusCounts(helper.readableDatabase, nic)
    fun getUpcomingBooking(nic: String, nowDate: String, nowTime: String): Booking? =
        bookingDao.getUpcoming(helper.readableDatabase, nic, nowDate, nowTime)

    // ---- Transactions (read) ----

    fun getTransactionById(transactionId: String): Transaction? = transactionDao.getById(helper.readableDatabase, transactionId)
    fun getTransactionByQrToken(qrToken: String): Transaction? = transactionDao.getByQrToken(helper.readableDatabase, qrToken)
    fun getTransactionByBookingId(bookingId: String): Transaction? = transactionDao.getByBookingId(helper.readableDatabase, bookingId)
    fun getPendingTransfersCount(): Int = transactionDao.countByStatus(helper.readableDatabase, TransactionStatus.GENERATED)
    fun getCompletedTransfersCount(startMillis: Long, endMillis: Long): Int =
        transactionDao.countCompletedBetween(helper.readableDatabase, startMillis, endMillis)

    // ---- Session (non-authoritative mirror) ----

    fun saveSessionMirror(session: Session) = sessionDao.save(helper.writableDatabase, session)
    fun getSessionMirror(): Session? = sessionDao.get(helper.readableDatabase)
    fun clearSessionMirror() = sessionDao.clear(helper.readableDatabase)

    // ---- Atomic multi-table operations ----

    /**
     * Creates a booking and consumes one station slot in a single transaction. Re-fetches the
     * station row inside the transaction (not a value the caller loaded earlier) so two
     * simultaneous bookings can't both succeed against the same last slot.
     */
    fun createBooking(
        prosumerNic: String,
        stationId: String,
        bookingDate: String,
        bookingTime: String,
        energyAmount: Double,
        now: Long
    ): AppResult<Booking> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val station = stationDao.getById(db, stationId)
                ?: return AppResult.Failure(AppError.NotFound)
            if (station.status != StationStatus.ACTIVE || station.availableSlots <= 0) {
                return AppResult.Failure(AppError.Unknown("Station is not available for booking"))
            }

            val booking = Booking(
                bookingId = UUID.randomUUID().toString(),
                prosumerNic = prosumerNic,
                stationId = stationId,
                bookingDate = bookingDate,
                bookingTime = bookingTime,
                energyAmount = energyAmount,
                status = BookingStatus.PENDING,
                syncStatus = SyncStatus.PENDING_SYNC,
                createdAt = now,
                updatedAt = now
            )
            bookingDao.insert(db, booking)
            stationDao.adjustAvailableSlots(db, stationId, delta = -1)

            db.setTransactionSuccessful()
            return AppResult.Success(booking)
        } finally {
            db.endTransaction()
        }
    }
}