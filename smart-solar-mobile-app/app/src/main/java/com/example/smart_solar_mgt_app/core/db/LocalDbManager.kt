package com.example.smart_solar_mgt_app.core.db

import android.content.Context
import com.example.smart_solar_mgt_app.core.common.AppError
import com.example.smart_solar_mgt_app.core.common.AppResult
import com.example.smart_solar_mgt_app.core.common.SyncStatus
import com.example.smart_solar_mgt_app.core.db.dao.BookingDao
import com.example.smart_solar_mgt_app.core.db.dao.CachedNodeDao
import com.example.smart_solar_mgt_app.core.db.dao.SessionDao
import com.example.smart_solar_mgt_app.core.db.dao.SyncOutboxDao
import com.example.smart_solar_mgt_app.core.db.dao.UserDao
import com.example.smart_solar_mgt_app.domain.model.AccountStatus
import com.example.smart_solar_mgt_app.domain.model.Booking
import com.example.smart_solar_mgt_app.domain.model.BookingListItem
import com.example.smart_solar_mgt_app.domain.model.BookingStatus
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
import com.example.smart_solar_mgt_app.domain.model.OutboxOperation
import com.example.smart_solar_mgt_app.domain.model.OutboxOperationType
import com.example.smart_solar_mgt_app.domain.model.Role
import com.example.smart_solar_mgt_app.domain.model.Session
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
    private val bookingDao = BookingDao()
    private val sessionDao = SessionDao()
    private val syncOutboxDao = SyncOutboxDao()
    private val cachedNodeDao = CachedNodeDao()

    // ---- Users ----

    fun insertUser(user: User) = userDao.insert(helper.writableDatabase, user)
    fun updateUser(user: User) = userDao.update(helper.writableDatabase, user)
    fun updateAccountStatus(nic: String, status: AccountStatus) = userDao.updateAccountStatus(helper.writableDatabase, nic, status)
    fun getUserByNic(nic: String): User? = userDao.getByNic(helper.readableDatabase, nic)
    fun getUserByEmail(email: String): User? = userDao.getByEmail(helper.readableDatabase, email)
    fun getUsersByRole(role: Role): List<User> = userDao.getByRole(helper.readableDatabase, role)
    fun getUsersByRoleAndStatus(role: Role, status: AccountStatus): List<User> =
        userDao.getByRoleAndStatus(helper.readableDatabase, role, status)

    /** See AuthRepository.upsertProsumerProfileCache. Inserts if this NIC has no local row yet
     * (a different device, or one that skipped local self-registration); otherwise updates the
     * existing row in place - notably including one still stuck at PENDING_APPROVAL from this
     * device's own registration, now that the server has confirmed it's actually usable.
     * passwordHash is left null either way. */
    fun upsertProsumerProfileCache(nic: String, fullName: String, email: String, phone: String?, address: String?) {
        val db = helper.writableDatabase
        val existing = userDao.getByNic(db, nic)
        val cached = User(
            nic = nic,
            name = fullName,
            email = email,
            phone = phone,
            address = address,
            passwordHash = null,
            role = Role.PROSUMER,
            accountStatus = AccountStatus.ACTIVE
        )
        if (existing != null) userDao.update(db, cached) else userDao.insert(db, cached)
    }

    // ---- Bookings (read + write-through cache from RemoteReservationRepository) ----

    fun getBookingById(bookingId: String): Booking? = bookingDao.getById(helper.readableDatabase, bookingId)
    fun getBookingsByProsumer(nic: String): List<Booking> = bookingDao.getByProsumer(helper.readableDatabase, nic)
    fun getBookingsByStatus(status: BookingStatus): List<Booking> = bookingDao.getByStatus(helper.readableDatabase, status)
    fun getPendingSyncBookings(): List<Booking> = bookingDao.getPendingSync(helper.readableDatabase)
    fun getBookingStatusCounts(nic: String): Map<BookingStatus, Int> = bookingDao.getStatusCounts(helper.readableDatabase, nic)
    fun getUpcomingBooking(nic: String, nowDate: String, nowTime: String): Booking? =
        bookingDao.getUpcoming(helper.readableDatabase, nic, nowDate, nowTime)
    fun getApprovedFutureCount(nic: String, nowDate: String, nowTime: String): Int =
        bookingDao.countApprovedFuture(helper.readableDatabase, nic, nowDate, nowTime)

    /** Single JOIN query backing the whole My Bookings screen - tabs/search are applied in-memory on top of this. */
    fun getBookingListItems(nic: String): List<BookingListItem> =
        queryBookingListItems(whereProsumerNic = nic)

    /** Cross-prosumer variant backing the Grid Operator's Bookings overview - same JOIN, no nic filter. */
    fun getAllBookingListItems(): List<BookingListItem> = queryBookingListItems(whereProsumerNic = null)

    // LEFT JOIN (not JOIN) against the node cache: a booking's node may not be in this device's
    // cache (e.g. fresh install, cache cleared, or a node this device never browsed) - falling
    // back to the raw nodeId rather than dropping the row entirely, same precedent as the
    // backend's own ReservationResponse.NodeName enrichment and OperatorHomeViewModel's
    // `stationNames[booking.stationId] ?: booking.stationId`.
    private fun queryBookingListItems(whereProsumerNic: String?): List<BookingListItem> {
        val b = DatabaseContract.Bookings
        val n = DatabaseContract.CachedNodes
        val where = if (whereProsumerNic != null) "WHERE b.${b.COL_PROSUMER_NIC} = ?" else ""
        val sql = """
            SELECT b.${b.COL_BOOKING_ID}, b.${b.COL_PROSUMER_NIC}, b.${b.COL_NODE_ID}, b.${b.COL_BOOKING_DATE}, b.${b.COL_BOOKING_TIME},
                   b.${b.COL_ENERGY_AMOUNT}, b.${b.COL_STATUS}, b.${b.COL_SYNC_STATUS}, n.${n.COL_NAME}
            FROM ${b.TABLE} b
            LEFT JOIN ${n.TABLE} n ON n.${n.COL_NODE_ID} = b.${b.COL_NODE_ID}
            $where
            ORDER BY b.${b.COL_BOOKING_DATE} DESC, b.${b.COL_BOOKING_TIME} DESC
        """
        val args = if (whereProsumerNic != null) arrayOf(whereProsumerNic) else null
        helper.readableDatabase.rawQuery(sql, args).use { cursor ->
            val results = mutableListOf<BookingListItem>()
            while (cursor.moveToNext()) {
                val nodeId = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_NODE_ID))
                val nodeNameIndex = cursor.getColumnIndexOrThrow(n.COL_NAME)
                results.add(
                    BookingListItem(
                        bookingId = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_BOOKING_ID)),
                        prosumerNic = cursor.getString(cursor.getColumnIndexOrThrow(b.COL_PROSUMER_NIC)),
                        nodeId = nodeId,
                        stationName = if (cursor.isNull(nodeNameIndex)) nodeId else cursor.getString(nodeNameIndex),
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

    // ---- Cached microgrid nodes (read-through cache for prosumer node/slot browsing, populated by RemoteNodeRepository) ----

    fun getCachedNodes(): List<MicrogridNode> = cachedNodeDao.getAll(helper.readableDatabase)
    fun getCachedNodeById(nodeId: String): MicrogridNode? = cachedNodeDao.getById(helper.readableDatabase, nodeId)

    /**
     * Replaces the entire node cache with a fresh fetch from the backend - a full refresh-and-
     * replace rather than incremental diffing, since node/slot data is small, read-only from this
     * device's perspective, and has no local writes of its own to reconcile.
     */
    fun replaceCachedNodes(nodes: List<MicrogridNode>) {
        val db = helper.writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            cachedNodeDao.deleteAll(db)
            for (node in nodes) cachedNodeDao.upsert(db, node, now)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    // ---- Session (non-authoritative mirror) ----

    fun saveSessionMirror(session: Session) = sessionDao.save(helper.writableDatabase, session)
    fun getSessionMirror(): Session? = sessionDao.get(helper.readableDatabase)
    fun clearSessionMirror() = sessionDao.clear(helper.readableDatabase)

    // ---- Sync outbox (core/sync/SyncManager.kt) ----

    fun enqueueOutboxOperation(operationType: OutboxOperationType, entityRef: String, payloadJson: String) {
        syncOutboxDao.insert(helper.writableDatabase, buildOutboxOperation(operationType, entityRef, payloadJson))
    }

    private fun buildOutboxOperation(operationType: OutboxOperationType, entityRef: String, payloadJson: String): OutboxOperation {
        val now = System.currentTimeMillis()
        return OutboxOperation(
            id = UUID.randomUUID().toString(),
            operationType = operationType,
            entityRef = entityRef,
            payloadJson = payloadJson,
            status = SyncStatus.PENDING_SYNC,
            attemptCount = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun getPendingSyncOutboxOperations(): List<OutboxOperation> = syncOutboxDao.getPendingSync(helper.readableDatabase)

    fun getOutboxOperationForEntity(operationType: OutboxOperationType, entityRef: String): OutboxOperation? =
        syncOutboxDao.getByEntityRef(helper.readableDatabase, operationType, entityRef)

    fun deleteOutboxOperation(id: String) = syncOutboxDao.delete(helper.writableDatabase, id)

    fun recordOutboxAttemptFailure(id: String, error: String) =
        syncOutboxDao.recordAttemptFailure(helper.writableDatabase, id, error, System.currentTimeMillis())

    fun markOutboxOperationRejected(id: String, error: String) =
        syncOutboxDao.markRejected(helper.writableDatabase, id, error, System.currentTimeMillis())

    // ---- Atomic multi-table operations ----

    /**
     * Local-first self-registration: writes the prosumer's own `users` row (no password - see
     * User.passwordHash) and queues a PROSUMER_REGISTER outbox entry in one transaction, so a
     * registration made with no connectivity survives an app restart and is guaranteed to be
     * sent exactly once SyncWorker gets to run it (see RegisterViewModel).
     */
    fun registerProsumerLocally(
        nic: String,
        fullName: String,
        email: String,
        phone: String?,
        address: String?,
        payloadJson: String
    ): AppResult<Unit> {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            if (userDao.getByNic(db, nic) != null) {
                return AppResult.Failure(AppError.UniqueConstraintViolation("nic"))
            }
            if (userDao.getByEmail(db, email) != null) {
                return AppResult.Failure(AppError.UniqueConstraintViolation("email"))
            }

            val user = User(
                nic = nic,
                name = fullName,
                email = email,
                phone = phone,
                address = address,
                passwordHash = null,
                role = Role.PROSUMER,
                accountStatus = AccountStatus.PENDING_APPROVAL
            )
            userDao.insert(db, user)
            syncOutboxDao.insert(db, buildOutboxOperation(OutboxOperationType.PROSUMER_REGISTER, nic, payloadJson))

            db.setTransactionSuccessful()
            return AppResult.Success(Unit)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Local-first reservation request: writes a Pending/PENDING_SYNC row (a client-generated id,
     * since the backend hasn't assigned a real one yet) and queues a RESERVATION_CREATE outbox
     * entry, atomically - see BookingRepositoryImpl.createBooking. Business-rule enforcement
     * (slot availability, prosumer/node status) happens server-side once SyncWorker actually
     * submits this; a rejection surfaces later via markBookingSyncFailed, not synchronously here.
     */
    fun createBookingLocally(booking: Booking, payloadJson: String) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            bookingDao.insert(db, booking)
            syncOutboxDao.insert(db, buildOutboxOperation(OutboxOperationType.RESERVATION_CREATE, booking.bookingId, payloadJson))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Local-first reschedule: stamps the new date/time (forcing PENDING_SYNC) and queues a
     * RESERVATION_UPDATE outbox entry, atomically. */
    fun updateBookingLocally(bookingId: String, bookingDate: String, bookingTime: String, payloadJson: String) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            bookingDao.updateSchedule(db, bookingId, bookingDate, bookingTime, System.currentTimeMillis())
            syncOutboxDao.insert(db, buildOutboxOperation(OutboxOperationType.RESERVATION_UPDATE, bookingId, payloadJson))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Local-first cancel: stamps CANCELLED (forcing PENDING_SYNC) and queues a
     * RESERVATION_CANCEL outbox entry, atomically. */
    fun cancelBookingLocally(bookingId: String) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            bookingDao.updateStatus(db, bookingId, BookingStatus.CANCELLED, SyncStatus.PENDING_SYNC, System.currentTimeMillis())
            syncOutboxDao.insert(db, buildOutboxOperation(OutboxOperationType.RESERVATION_CANCEL, bookingId, "{}"))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Once SyncWorker successfully syncs a RESERVATION_CREATE, replaces the local row's client-
     * generated temporary id with the backend's real reservation id, atomically. A screen still
     * showing the temp id at the exact moment this runs (unlikely - screens load once, not
     * continuously) would find it gone on a later re-fetch - a known, narrow limitation of
     * swapping a client-generated id for a server-assigned one, which this app's other
     * offline-first flows don't need (Prosumer registration's natural key, NIC, never changes).
     */
    fun reconcileCreatedBooking(oldLocalId: String, confirmed: Booking) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            bookingDao.delete(db, oldLocalId)
            bookingDao.upsert(db, confirmed)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Flips a locally-cached booking's sync_status to SYNCED once its queued RESERVATION_UPDATE/
     * RESERVATION_CANCEL has been confirmed by the backend - the business status/fields were
     * already set optimistically at enqueue time and echo exactly what was sent, so there's
     * nothing else to update. */
    fun markBookingSynced(bookingId: String) = bookingDao.updateSyncStatus(helper.writableDatabase, bookingId, SyncStatus.SYNCED)

    /** Flips a locally-cached booking's sync_status to SYNC_FAILED after its queued write was
     * definitively rejected by the backend (see SyncWorker) - kept visible, never retried, same
     * precedent as the outbox row itself. */
    fun markBookingSyncFailed(bookingId: String) = bookingDao.updateSyncStatus(helper.writableDatabase, bookingId, SyncStatus.SYNC_FAILED)

}