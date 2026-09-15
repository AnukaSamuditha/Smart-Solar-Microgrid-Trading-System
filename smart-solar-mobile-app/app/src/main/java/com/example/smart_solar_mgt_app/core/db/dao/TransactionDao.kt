package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.db.DatabaseContract
import com.example.smart_solar_mgt_app.domain.model.Transaction
import com.example.smart_solar_mgt_app.domain.model.TransactionStatus

class TransactionDao {

    private val T = DatabaseContract.Transactions

    fun insert(db: SQLiteDatabase, transaction: Transaction) {
        db.insertOrThrow(T.TABLE, null, transaction.toContentValues())
    }

    fun getById(db: SQLiteDatabase, transactionId: String): Transaction? {
        db.query(T.TABLE, null, "${T.COL_TRANSACTION_ID} = ?", arrayOf(transactionId), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTransaction() else null
        }
    }

    fun getByQrToken(db: SQLiteDatabase, qrToken: String): Transaction? {
        db.query(T.TABLE, null, "${T.COL_QR_TOKEN} = ?", arrayOf(qrToken), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTransaction() else null
        }
    }

    fun getByBookingId(db: SQLiteDatabase, bookingId: String): Transaction? {
        db.query(T.TABLE, null, "${T.COL_BOOKING_ID} = ?", arrayOf(bookingId), null, null, null, "1").use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTransaction() else null
        }
    }

    /** The transaction for a booking that is still GENERATED or SCANNED (not yet terminal). */
    fun getActiveForBooking(db: SQLiteDatabase, bookingId: String): Transaction? {
        val selection = "${T.COL_BOOKING_ID} = ? AND ${T.COL_STATUS} IN ('GENERATED','SCANNED')"
        db.query(T.TABLE, null, selection, arrayOf(bookingId), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toTransaction() else null
        }
    }

    fun updateStatus(
        db: SQLiteDatabase,
        transactionId: String,
        status: TransactionStatus,
        operatorId: String? = null,
        completedAt: Long? = null
    ) {
        val values = ContentValues().apply {
            put(T.COL_STATUS, status.name)
            if (operatorId != null) put(T.COL_OPERATOR_ID, operatorId)
            if (completedAt != null) put(T.COL_COMPLETED_AT, completedAt)
        }
        db.update(T.TABLE, values, "${T.COL_TRANSACTION_ID} = ?", arrayOf(transactionId))
    }

    fun countByStatus(db: SQLiteDatabase, status: TransactionStatus): Int {
        db.rawQuery("SELECT COUNT(*) FROM ${T.TABLE} WHERE ${T.COL_STATUS} = ?", arrayOf(status.name)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    /** Count of COMPLETED transactions whose completed_at falls within [startMillis, endMillis). */
    fun countCompletedBetween(db: SQLiteDatabase, startMillis: Long, endMillis: Long): Int {
        val sql = "SELECT COUNT(*) FROM ${T.TABLE} WHERE ${T.COL_STATUS} = 'COMPLETED' " +
            "AND ${T.COL_COMPLETED_AT} >= ? AND ${T.COL_COMPLETED_AT} < ?"
        db.rawQuery(sql, arrayOf(startMillis.toString(), endMillis.toString())).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun Transaction.toContentValues(): ContentValues = ContentValues().apply {
        put(T.COL_TRANSACTION_ID, transactionId)
        put(T.COL_BOOKING_ID, bookingId)
        put(T.COL_QR_TOKEN, qrToken)
        put(T.COL_STATUS, status.name)
        put(T.COL_OPERATOR_ID, operatorId)
        put(T.COL_GENERATED_AT, generatedAt)
        put(T.COL_COMPLETED_AT, completedAt)
    }

    private fun Cursor.toTransaction(): Transaction = Transaction(
        transactionId = getString(getColumnIndexOrThrow(T.COL_TRANSACTION_ID)),
        bookingId = getString(getColumnIndexOrThrow(T.COL_BOOKING_ID)),
        qrToken = getString(getColumnIndexOrThrow(T.COL_QR_TOKEN)),
        status = TransactionStatus.valueOf(getString(getColumnIndexOrThrow(T.COL_STATUS))),
        operatorId = getString(getColumnIndexOrThrow(T.COL_OPERATOR_ID)),
        generatedAt = getLong(getColumnIndexOrThrow(T.COL_GENERATED_AT)),
        completedAt = if (isNull(getColumnIndexOrThrow(T.COL_COMPLETED_AT))) null else getLong(getColumnIndexOrThrow(T.COL_COMPLETED_AT))
    )
}