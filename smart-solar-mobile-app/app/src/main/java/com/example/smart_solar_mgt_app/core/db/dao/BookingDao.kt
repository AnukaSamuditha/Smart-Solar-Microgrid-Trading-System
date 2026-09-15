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

    fun getByStation(db: SQLiteDatabase, stationId: String): List<Booking> {
        db.query(Bookings.TABLE, null, "${Bookings.COL_STATION_ID} = ?", arrayOf(stationId), null, null, null).use { cursor ->
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

    /** status_counts grouped for one prosumer, e.g. {PENDING=2, CONFIRMED=1}. */
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

    /** Soonest active (PENDING/CONFIRMED) booking at or after [nowDate]/[nowTime]. */
    fun getUpcoming(db: SQLiteDatabase, nic: String, nowDate: String, nowTime: String): Booking? {
        val selection = "${Bookings.COL_PROSUMER_NIC} = ? " +
            "AND ${Bookings.COL_STATUS} IN ('PENDING','CONFIRMED') " +
            "AND (${Bookings.COL_BOOKING_DATE} > ? OR (${Bookings.COL_BOOKING_DATE} = ? AND ${Bookings.COL_BOOKING_TIME} >= ?))"
        val orderBy = "${Bookings.COL_BOOKING_DATE} ASC, ${Bookings.COL_BOOKING_TIME} ASC"
        db.query(Bookings.TABLE, null, selection, arrayOf(nic, nowDate, nowDate, nowTime), null, null, orderBy, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toBooking() else null
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

    /** Updates the editable fields of a modify (date/time/energy), stamping sync_status + updated_at. */
    fun updateFields(
        db: SQLiteDatabase,
        bookingId: String,
        bookingDate: String,
        bookingTime: String,
        energyAmount: Double,
        syncStatus: SyncStatus,
        updatedAt: Long
    ) {
        val values = ContentValues().apply {
            put(Bookings.COL_BOOKING_DATE, bookingDate)
            put(Bookings.COL_BOOKING_TIME, bookingTime)
            put(Bookings.COL_ENERGY_AMOUNT, energyAmount)
            put(Bookings.COL_SYNC_STATUS, syncStatus.name)
            put(Bookings.COL_UPDATED_AT, updatedAt)
        }
        db.update(Bookings.TABLE, values, "${Bookings.COL_BOOKING_ID} = ?", arrayOf(bookingId))
    }

    private fun Cursor.toBookingList(): List<Booking> {
        val results = mutableListOf<Booking>()
        while (moveToNext()) results.add(toBooking())
        return results
    }

    private fun Booking.toContentValues(): ContentValues = ContentValues().apply {
        put(Bookings.COL_BOOKING_ID, bookingId)
        put(Bookings.COL_PROSUMER_NIC, prosumerNic)
        put(Bookings.COL_STATION_ID, stationId)
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
        stationId = getString(getColumnIndexOrThrow(Bookings.COL_STATION_ID)),
        bookingDate = getString(getColumnIndexOrThrow(Bookings.COL_BOOKING_DATE)),
        bookingTime = getString(getColumnIndexOrThrow(Bookings.COL_BOOKING_TIME)),
        energyAmount = getDouble(getColumnIndexOrThrow(Bookings.COL_ENERGY_AMOUNT)),
        status = BookingStatus.valueOf(getString(getColumnIndexOrThrow(Bookings.COL_STATUS))),
        syncStatus = SyncStatus.valueOf(getString(getColumnIndexOrThrow(Bookings.COL_SYNC_STATUS))),
        createdAt = getLong(getColumnIndexOrThrow(Bookings.COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(Bookings.COL_UPDATED_AT))
    )
}