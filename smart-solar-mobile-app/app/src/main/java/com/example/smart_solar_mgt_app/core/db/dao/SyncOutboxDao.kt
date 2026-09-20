package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.SyncOutbox
import com.example.smart_solar_mgt_app.domain.model.OutboxOperation
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType

class SyncOutboxDao {

    fun insert(db: SQLiteDatabase, operation: OutboxOperation) {
        db.insertOrThrow(SyncOutbox.TABLE, null, operation.toContentValues())
    }

    /** Every row not yet confirmed by the server - PENDING_SYNC (never attempted, or a
     * transient failure worth retrying) and SYNC_FAILED (kept visible, but SyncWorker skips it). */
    fun getPendingSync(db: SQLiteDatabase): List<OutboxOperation> {
        val selection = "${SyncOutbox.COL_STATUS} = ?"
        db.query(SyncOutbox.TABLE, null, selection, arrayOf(SyncStatus.PENDING_SYNC.name), null, null, "${SyncOutbox.COL_CREATED_AT} ASC")
            .use { cursor ->
                val results = mutableListOf<OutboxOperation>()
                while (cursor.moveToNext()) results.add(cursor.toOutboxOperation())
                return results
            }
    }

    fun getByEntityRef(db: SQLiteDatabase, operationType: OutboxOperationType, entityRef: String): OutboxOperation? {
        val selection = "${SyncOutbox.COL_OPERATION_TYPE} = ? AND ${SyncOutbox.COL_ENTITY_REF} = ?"
        db.query(SyncOutbox.TABLE, null, selection, arrayOf(operationType.name, entityRef), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toOutboxOperation() else null
        }
    }

    fun delete(db: SQLiteDatabase, id: String) {
        db.delete(SyncOutbox.TABLE, "${SyncOutbox.COL_ID} = ?", arrayOf(id))
    }

    /** A transient failure (network) - left PENDING_SYNC so the next sync run retries it. */
    fun recordAttemptFailure(db: SQLiteDatabase, id: String, error: String, now: Long) {
        val values = ContentValues().apply {
            put(SyncOutbox.COL_LAST_ERROR, error)
            put(SyncOutbox.COL_UPDATED_AT, now)
        }
        db.execSQL(
            "UPDATE ${SyncOutbox.TABLE} SET ${SyncOutbox.COL_ATTEMPT_COUNT} = ${SyncOutbox.COL_ATTEMPT_COUNT} + 1 WHERE ${SyncOutbox.COL_ID} = ?",
            arrayOf(id)
        )
        db.update(SyncOutbox.TABLE, values, "${SyncOutbox.COL_ID} = ?", arrayOf(id))
    }

    /** A definitive server rejection (e.g. NIC already in use) - never retried, kept for the user to see. */
    fun markRejected(db: SQLiteDatabase, id: String, error: String, now: Long) {
        val values = ContentValues().apply {
            put(SyncOutbox.COL_STATUS, SyncStatus.SYNC_FAILED.name)
            put(SyncOutbox.COL_LAST_ERROR, error)
            put(SyncOutbox.COL_UPDATED_AT, now)
        }
        db.update(SyncOutbox.TABLE, values, "${SyncOutbox.COL_ID} = ?", arrayOf(id))
    }

    private fun OutboxOperation.toContentValues(): ContentValues = ContentValues().apply {
        put(SyncOutbox.COL_ID, id)
        put(SyncOutbox.COL_OPERATION_TYPE, operationType.name)
        put(SyncOutbox.COL_ENTITY_REF, entityRef)
        put(SyncOutbox.COL_PAYLOAD_JSON, payloadJson)
        put(SyncOutbox.COL_STATUS, status.name)
        put(SyncOutbox.COL_ATTEMPT_COUNT, attemptCount)
        put(SyncOutbox.COL_LAST_ERROR, lastError)
        put(SyncOutbox.COL_CREATED_AT, createdAt)
        put(SyncOutbox.COL_UPDATED_AT, updatedAt)
    }

    private fun Cursor.toOutboxOperation(): OutboxOperation = OutboxOperation(
        id = getString(getColumnIndexOrThrow(SyncOutbox.COL_ID)),
        operationType = OutboxOperationType.valueOf(getString(getColumnIndexOrThrow(SyncOutbox.COL_OPERATION_TYPE))),
        entityRef = getString(getColumnIndexOrThrow(SyncOutbox.COL_ENTITY_REF)),
        payloadJson = getString(getColumnIndexOrThrow(SyncOutbox.COL_PAYLOAD_JSON)),
        status = SyncStatus.valueOf(getString(getColumnIndexOrThrow(SyncOutbox.COL_STATUS))),
        attemptCount = getInt(getColumnIndexOrThrow(SyncOutbox.COL_ATTEMPT_COUNT)),
        lastError = getString(getColumnIndexOrThrow(SyncOutbox.COL_LAST_ERROR)),
        createdAt = getLong(getColumnIndexOrThrow(SyncOutbox.COL_CREATED_AT)),
        updatedAt = getLong(getColumnIndexOrThrow(SyncOutbox.COL_UPDATED_AT))
    )
}
