package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.Bookings
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingStatus

class BookingDao {

    fun insert(db: SQLiteDatabase, booking: Booking) {
        db.insertOrThrow(Bookings.TABLE, null, booking.toContentValues())
    }

    /** Insert-or-replace, keyed by bookingId - used to write-through-cache a reservation the
     * backend just confirmed exists (create/update), whose id (the server's own reservation id)
     * this device may or may not already have a row for. */
    fun upsert(db: SQLiteDatabase, booking: Booking) {
        db.insertWithOnConflict(Bookings.TABLE, null, booking.toContentValues(), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getById(db: SQLiteDatabase, bookingId: String): Booking? {
        db.query(Bookings.TABLE, null, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBooking() else null
        }
    }

    fun getByProsumer(db: SQLiteDatabase, nic: String): List<Booking> {
        val orderBy = "${Bookings.COL_BOOKING_DATE} DESC, ${Bookings.COL_BOOKING_TIME} DESC"
        db.query(Bookings.TABLE, null, "${Bookings.COL_PROSUMER_NIC} = ?", arrayOf(nic), null, null, orderBy).use { cursor ->
            return cursor.toBookingList()
        }
    }

    fun getByStatus(db: SQLiteDatabase, status: BookingStatus): List<Booking> {
        db.query(Bookings.TABLE, null, "${Bookings.COL_STATUS} = ?", arrayOf(status.name), null, null, null).use { cursor ->
            return cursor.toBookingList()
        }
    }

    fun getPendingSync(db: SQLiteDatabase): List<Booking> {
        val selection = "${Bookings.COL_SYNC_STATUS} != ?"
        db.query(Bookings.TABLE, null, selection, arrayOf(SyncStatus.SYNCED.name), null, null, null).use { cursor ->
            return cursor.toBookingList()
        }
    }

    /** status_counts grouped for one prosumer, e.g. {PENDING=2, APPROVED=1}. */
    fun getStatusCounts(db: SQLiteDatabase, nic: String): Map<BookingStatus, Int> {
        val sql = "SELECT ${Bookings.COL_STATUS}, COUNT(*) AS cnt FROM ${Bookings.TABLE} " +
            "WHERE ${Bookings.COL_PROSUMER_NIC} = ? GROUP BY ${Bookings.COL_STATUS}"
        db.rawQuery(sql, arrayOf(nic)).use { cursor ->
            val results = mutableMapOf<BookingStatus, Int>()
            while (cursor.moveToNext()) {
                val status = BookingStatus.valueOf(cursor.getString(cursor.getColumnIndexOrThrow(Bookings.COL_STATUS)))
                results[status] = cursor.getInt(cursor.getColumnIndexOrThrow("cnt"))
            }
            return results
        }
    }

    /**
     * Shared "strictly later than [nowDate]/[nowTime]" predicate, used by both getUpcoming and
     * countApprovedFuture so the two can never silently disagree about what "future" means.
     * Relies on booking_date/booking_time being consistently zero-padded ISO strings (DateFormats)
     * so lexicographic string comparison is equivalent to chronological comparison.
     */
    private fun laterThanNowPredicate(): String =
        "(${Bookings.COL_BOOKING_DATE} > ? OR (${Bookings.COL_BOOKING_DATE} = ? AND ${Bookings.COL_BOOKING_TIME} > ?))"

    /** Soonest APPROVED booking strictly later than [nowDate]/[nowTime]. */
    fun getUpcoming(db: SQLiteDatabase, nic: String, nowDate: String, nowTime: String): Booking? {
        val selection = "${Bookings.COL_PROSUMER_NIC} = ? AND ${Bookings.COL_STATUS} = 'APPROVED' AND " +
            laterThanNowPredicate()
        val orderBy = "${Bookings.COL_BOOKING_DATE} ASC, ${Bookings.COL_BOOKING_TIME} ASC"
        db.query(Bookings.TABLE, null, selection, arrayOf(nic, nowDate, nowDate, nowTime), null, null, orderBy, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBooking() else null
        }
    }

    /** Count of this prosumer's APPROVED bookings strictly later than [nowDate]/[nowTime]. */
    fun countApprovedFuture(db: SQLiteDatabase, nic: String, nowDate: String, nowTime: String): Int {
        val sql = "SELECT COUNT(*) FROM ${Bookings.TABLE} " +
            "WHERE ${Bookings.COL_PROSUMER_NIC} = ? AND ${Bookings.COL_STATUS} = 'APPROVED' AND " +
            laterThanNowPredicate()
        db.rawQuery(sql, arrayOf(nic, nowDate, nowDate, nowTime)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun updateStatus(db: SQLiteDatabase, bookingId: String, status: BookingStatus, syncStatus: SyncStatus, updatedAt: Long) {
        val values = ContentValues().apply {
            put(Bookings.COL_STATUS, status.name)
            put(Bookings.COL_SYNC_STATUS, syncStatus.name)
            put(Bookings.COL_UPDATED_AT, updatedAt)
        }
        db.update(Bookings.TABLE, values, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId))
    }

    fun updateSyncStatus(db: SQLiteDatabase, bookingId: String, syncStatus: SyncStatus) {
        val values = ContentValues().apply { put(Bookings.COL_SYNC_STATUS, syncStatus.name) }
        db.update(Bookings.TABLE, values, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId))
    }

    /** Local-first reschedule: stamps the new date/time and forces PENDING_SYNC, since the
     * change hasn't reached the backend yet - see LocalDbManager.updateBookingLocally. */
    fun updateSchedule(db: SQLiteDatabase, bookingId: String, bookingDate: String, bookingTime: String, updatedAt: Long) {
        val values = ContentValues().apply {
            put(Bookings.COL_BOOKING_DATE, bookingDate)
            put(Bookings.COL_BOOKING_TIME, bookingTime)
            put(Bookings.COL_SYNC_STATUS, SyncStatus.PENDING_SYNC.name)
            put(Bookings.COL_UPDATED_AT, updatedAt)
        }
        db.update(Bookings.TABLE, values, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId))
    }

    /** Removes a booking row by id - used only to retire a client-generated temporary id once
     * RESERVATION_CREATE syncs and the backend's real id takes over (see
     * LocalDbManager.reconcileCreatedBooking). */
    fun delete(db: SQLiteDatabase, bookingId: String) {
        db.delete(Bookings.TABLE, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId))
    }

    private fun Cursor.toBookingList(): List<Booking> {
        val results = mutableListOf<Booking>()
        while (moveToNext()) results.add(toBooking())
        return results
    }

    private fun Booking.toContentValues(): ContentValues = ContentValues().apply {
        put(Bookings.COL_BOOKING_ID, bookingId)
        put(Bookings.COL_PROSUMER_NIC, prosumerNic)
        put(Bookings.COL_NODE_ID, nodeId)
        put(Bookings.COL_SLOT_ID, slotId)
        put(Bookings.COL_BOOKING_DATE, bookingDate)
        put(Bookings.COL_BOOKING_TIME, bookingTime)
        put(Bookings.COL_ENERGY_AMOUNT, energyAmount)
        put(Bookings.COL_STATUS, status.name)
        put(Bookings.COL_SYNC_STATUS, syncStatus.name)
        put(Bookings.COL_CREATED_AT, createdAt)
        put(Bookings.COL_UPDATED_AT, updatedAt)
    }

    private fun Cursor.toBooking(): Booking = Booking(
        bookingId = getString(getColumnIndexOrThrow(Bookings.COL_BOOKING_ID)),
        prosumerNic = getString(getColumnIndexOrThrow(Bookings.COL_PROSUMER_NIC)),
        nodeId = getString(getColumnIndexOrThrow(Bookings.COL_NODE_ID)),
        slotId = getString(getColumnIndexOrThrow(Bookings.COL_SLOT_ID)),
        bookingDate = getString(getColumnIndexOrThrow(Bookings.COL_BOOKING_DATE)),
        bookingTime = getString(getColumnIndexOrThrow(Bookings.COL_BOOKING_TIME)),
        energyAmount = getDouble(getColumnIndexOrThrow(Bookings.COL_ENERGY_AMOUNT)),
        status = BookingStatus.valueOf(getString(getColumnIndexOrThrow(Bookings.COL_STATUS))),
        syncStatus = SyncStatus.valueOf(getString(getColumnIndexOrThrow(Bookings.COL_SYNC_STATUS))),
        createdAt = getLong(getColumnIndexOrThrow(Bookings.COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(Bookings.COL_UPDATED_AT))
    )
}