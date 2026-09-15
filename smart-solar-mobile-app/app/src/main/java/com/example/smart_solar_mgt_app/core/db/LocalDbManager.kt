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
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.domain.model.StationStatus
import com.example.smart_solar_mgt_app.domain.model.Transaction
import com.example.smart_solar_mgt_app.domain.model.TransactionStatus
import com.example.smart_solar_mgt_app.domain.model.User
import com.example.smart_solar_mgt_app.util.BookingTimeRules
import java.time.LocalDate
import java.time.LocalTime
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

    /** Single JOIN query backing the whole My Bookings screen - tabs/search are applied in-memory on top of this. */
    fun getBookingListItems(nic: String): List<BookingListItem> {
        val b = DatabaseContract.Bookings
        val s = DatabaseContract.Stations
        val sql = """
            SELECT b.${b.COL_BOOKING_ID}, b.${b.COL_BOOKING_DATE}, b.${b.COL_BOOKING_TIME},
                   b.${b.COL_ENERGY_AMOUNT}, b.${b.COL_STATUS}, b.${b.COL_SYNC_STATUS},
                   s.${s.COL_STATION_ID}, s.${s.COL_STATION_NAME}
            FROM ${b.TABLE} b
            JOIN ${s.TABLE} s ON s.${s.COL_STATION_ID} = b.${b.COL_STATION_ID}
            WHERE b.${b.COL_PROSUMER_NIC} = ?
            ORDER BY b.${b.COL_BOOKING_DATE} DESC, b.${b.COL_BOOKING_TIME} DESC
        """
        helper.readableDatabase.rawQuery(sql, arrayOf(nic)).use { cursor ->
            val results = mutableListOf<BookingListItem>()
            while (cursor.moveToNext()) {
                results.add(
                    BookingListItem(
                        bookingId = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_BOOKING_ID)),
                        stationId = cursor.getString(cursor.getColumnIndexOrThrow(s.COL_STATION_ID)),
                        stationName = cursor.getString(cursor.getColumnIndexOrThrow(s.COL_STATION_NAME)),
                        bookingDate = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_BOOKING_DATE)),
                        bookingTime = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_BOOKING_TIME)),
                        energyAmount = cursor.getDouble(cursor.getColumnIndexOrThrow(b.COL_ENERGY_AMOUNT)),
                        status = BookingStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(b.COL_STATUS))),
                        syncStatus = SyncStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(b.COL_SYNC_STATUS)))
                    )
                )
            }
            return results
        }
    }

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

    /**
     * Modifies date/time/energy on an existing booking. Re-verifies ownership, status, and the
     * 12-hour notice rule inside the transaction - a client-side gate (BookingDetailFragment)
     * only decides whether to show the button; this is what's actually enforced.
     */
    fun updateBooking(
        bookingId: String,
        prosumerNic: String,
        bookingDate: String,
        bookingTime: String,
        energyAmount: Double,
        now: Long
    ): AppResult<Booking> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val current = bookingDao.getById(db, bookingId) ?: return AppResult.Failure(AppError.NotFound)
            if (current.prosumerNic != prosumerNic) return AppResult.Failure(AppError.Unauthorized)
            if (current.status != BookingStatus.PENDING && current.status != BookingStatus.CONFIRMED) {
                return AppResult.Failure(AppError.InvalidStatusTransition)
            }
            if (!canModifyOrCancel(current)) return AppResult.Failure(AppError.TooLateToModify)

            bookingDao.updateFields(db, bookingId, bookingDate, bookingTime, energyAmount, SyncStatus.PENDING_SYNC, now)
            db.setTransactionSuccessful()
            return AppResult.Success(bookingDao.getById(db, bookingId)!!)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Cancels an existing booking and restores its station slot. Same ownership/status/12-hour
     * re-check as updateBooking. Double-cancel (double-tap, or a race) is naturally prevented -
     * the second attempt sees status already CANCELLED and fails InvalidStatusTransition instead
     * of restoring the slot twice.
     */
    fun cancelBooking(bookingId: String, prosumerNic: String, now: Long): AppResult<Unit> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val current = bookingDao.getById(db, bookingId) ?: return AppResult.Failure(AppError.NotFound)
            if (current.prosumerNic != prosumerNic) return AppResult.Failure(AppError.Unauthorized)
            if (current.status != BookingStatus.PENDING && current.status != BookingStatus.CONFIRMED) {
                return AppResult.Failure(AppError.InvalidStatusTransition)
            }
            if (!canModifyOrCancel(current)) return AppResult.Failure(AppError.TooLateToModify)

            bookingDao.updateStatus(db, bookingId, BookingStatus.CANCELLED, SyncStatus.PENDING_SYNC, now)
            stationDao.adjustAvailableSlots(db, current.stationId, delta = 1)
            // NOTE: once the QR/transaction flow exists, also flip any GENERATED transaction for
            // this booking to CANCELLED here, so an already-cancelled reservation's QR can't
            // still be scanned/completed by an operator who hasn't refreshed.

            db.setTransactionSuccessful()
            return AppResult.Success(Unit)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * PENDING -> CONFIRMED, plus generates the QR transaction row (Energy Transfer Pass).
     * The signed token itself is computed by the caller (QrTokenService, a core.security
     * concern) - this just persists it atomically alongside the status change.
     */
    fun approveBooking(bookingId: String, transactionId: String, qrToken: String): AppResult<Booking> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val current = bookingDao.getById(db, bookingId) ?: return AppResult.Failure(AppError.NotFound)
            if (current.status != BookingStatus.PENDING) return AppResult.Failure(AppError.InvalidStatusTransition)

            val now = System.currentTimeMillis()
            bookingDao.updateStatus(db, bookingId, BookingStatus.CONFIRMED, SyncStatus.PENDING_SYNC, now)

            val transaction = Transaction(
                transactionId = transactionId,
                bookingId = bookingId,
                qrToken = qrToken,
                status = TransactionStatus.GENERATED,
                operatorId = null,
                generatedAt = now,
                completedAt = null
            )
            transactionDao.insert(db, transaction)

            db.setTransactionSuccessful()
            return AppResult.Success(bookingDao.getById(db, bookingId)!!)
        } finally {
            db.endTransaction()
        }
    }

    /** Operator rejection of a still-PENDING booking - no ownership/12-hour check (that's a prosumer-cancel concept), just status + slot restore. */
    fun rejectBooking(bookingId: String): AppResult<Unit> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val current = bookingDao.getById(db, bookingId) ?: return AppResult.Failure(AppError.NotFound)
            if (current.status != BookingStatus.PENDING) return AppResult.Failure(AppError.InvalidStatusTransition)

            val now = System.currentTimeMillis()
            bookingDao.updateStatus(db, bookingId, BookingStatus.CANCELLED, SyncStatus.PENDING_SYNC, now)
            stationDao.adjustAvailableSlots(db, current.stationId, delta = 1)

            db.setTransactionSuccessful()
            return AppResult.Success(Unit)
        } finally {
            db.endTransaction()
        }
    }

    private fun canModifyOrCancel(booking: Booking): Boolean = BookingTimeRules.canModifyOrCancel(
        booking.status,
        LocalDate.parse(booking.bookingDate),
        LocalTime.parse(booking.bookingTime)
    )
}