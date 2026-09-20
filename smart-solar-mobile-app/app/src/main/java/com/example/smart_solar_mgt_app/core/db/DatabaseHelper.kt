package com.example.smart_solar_mgt_app.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.smart_solar_mgt_app.core.security.PasswordHasher

/**
 * Owns schema creation/migration for the local SQLite database. This is the ONLY class that
 * should ever call getReadableDatabase()/getWritableDatabase() - everything else goes through
 * LocalDbManager.
 */
class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DatabaseContract.DATABASE_NAME, null, DatabaseContract.DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        // Foreign key enforcement is turned on in onOpen(), not here. onCreate/onUpgrade run
        // inside a transaction the framework opens for us, and SQLite treats
        // "PRAGMA foreign_keys" as a no-op while a transaction is active - so a migration that
        // rebuilds a table referenced by an ON DELETE CASCADE (bookings is referenced by
        // transactions) needs enforcement to have never been turned on yet during that
        // transaction, since with it on, DROP TABLE performs an implicit DELETE FROM and would
        // cascade-delete every matching row in the referencing table.
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(DatabaseContract.Users.CREATE_TABLE)
        db.execSQL(DatabaseContract.Bookings.CREATE_TABLE)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_PROSUMER)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_NODE)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_SYNC)
        db.execSQL(DatabaseContract.Transactions.CREATE_TABLE)
        db.execSQL(DatabaseContract.Transactions.CREATE_INDEX_BOOKING)
        db.execSQL(DatabaseContract.SessionTable.CREATE_TABLE)
        db.execSQL(DatabaseContract.SyncOutbox.CREATE_TABLE)
        db.execSQL(DatabaseContract.SyncOutbox.CREATE_INDEX_STATUS)
        db.execSQL(DatabaseContract.CachedNodes.CREATE_TABLE)

        seedDemoProsumer(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateBookingStatusConfirmedToApproved(db)
        }
        if (oldVersion < 3) {
            // Adds the demo prosumer to installs that already exist on-device (not just fresh
            // ones via onCreate) - CONFLICT_IGNORE makes this safe to run against a database that
            // already has this row, or where the fixed demo NIC happens to collide with something
            // a real registration created. (The sample-bookings half of this step was removed
            // once the v6->v7 migration below dropped the fictional local-only stations those
            // demo bookings referenced.)
            seedDemoProsumer(db)
        }
        if (oldVersion < 4) {
            // Grid Operators now authenticate against the real backend (see SecurityManagerImpl -
            // POST /api/v1/auth/login) instead of this local seeded account; drop it from installs
            // that already have it so it can't shadow the remote login path by matching locally first.
            removeSeededGridOperatorAccount(db)
        }
        if (oldVersion < 5) {
            // Self-registered prosumers (RegisterViewModel) now write a local row with no
            // password until approved + reset (see ResetPasswordActivity), so password_hash can
            // no longer be NOT NULL - and the offline outbox (core/sync/SyncManager.kt) needs
            // somewhere to queue backend operations attempted with no connectivity.
            migrateUsersPasswordHashNullable(db)
            db.execSQL(DatabaseContract.SyncOutbox.CREATE_TABLE)
            db.execSQL(DatabaseContract.SyncOutbox.CREATE_INDEX_STATUS)
        }
        if (oldVersion < 6) {
            // Additive only - the prosumer-browsable node/slot cache (RemoteNodeRepository)
            // doesn't touch or replace `stations` yet; that happens in the v6->v7 step below.
            db.execSQL(DatabaseContract.CachedNodes.CREATE_TABLE)
        }
        if (oldVersion < 7) {
            migrateBookingsToNodesAndSlots(db)
        }
        if (oldVersion < 8) {
            migrateSyncOutboxAddReservationOperationTypes(db)
        }
        // Future schema changes land here as further incremental `if (oldVersion < N)` steps,
        // never a single drop-and-recreate of the whole database.
    }

    private fun removeSeededGridOperatorAccount(db: SQLiteDatabase) {
        db.delete(DatabaseContract.Users.TABLE, "${DatabaseContract.Users.COL_NIC} = ?", arrayOf("OP0000001"))
    }

    // v4 -> v5: drops the NOT NULL constraint on users.password_hash. SQLite can't ALTER a
    // column's NOT NULL in place, so this rebuilds the table (same technique as
    // migrateBookingStatusConfirmedToApproved) - existing rows all have a real hash already, so
    // the copy is a plain passthrough.
    private fun migrateUsersPasswordHashNullable(db: SQLiteDatabase) {
        val u = DatabaseContract.Users

        db.execSQL(
            """
            CREATE TABLE users_new (
                ${u.COL_NIC} TEXT PRIMARY KEY,
                ${u.COL_NAME} TEXT NOT NULL,
                ${u.COL_EMAIL} TEXT NOT NULL UNIQUE,
                ${u.COL_PHONE} TEXT,
                ${u.COL_ADDRESS} TEXT,
                ${u.COL_PASSWORD_HASH} TEXT,
                ${u.COL_ROLE} TEXT NOT NULL CHECK(${u.COL_ROLE} IN ('PROSUMER','GRID_OPERATOR')),
                ${u.COL_ACCOUNT_STATUS} TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK(${u.COL_ACCOUNT_STATUS} IN (
                        'PENDING_APPROVAL','ACTIVE','SUSPENDED','REJECTED',
                        'DEACTIVATION_REQUESTED','DEACTIVATED'
                    ))
            )
            """
        )

        db.execSQL(
            """
            INSERT INTO users_new (
                ${u.COL_NIC}, ${u.COL_NAME}, ${u.COL_EMAIL}, ${u.COL_PHONE}, ${u.COL_ADDRESS},
                ${u.COL_PASSWORD_HASH}, ${u.COL_ROLE}, ${u.COL_ACCOUNT_STATUS}
            )
            SELECT
                ${u.COL_NIC}, ${u.COL_NAME}, ${u.COL_EMAIL}, ${u.COL_PHONE}, ${u.COL_ADDRESS},
                ${u.COL_PASSWORD_HASH}, ${u.COL_ROLE}, ${u.COL_ACCOUNT_STATUS}
            FROM ${u.TABLE}
            """
        )

        db.execSQL("DROP TABLE ${u.TABLE}")
        db.execSQL("ALTER TABLE users_new RENAME TO ${u.TABLE}")
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * v1 -> v2: renames the bookings.status value 'CONFIRMED' to 'APPROVED' (terminology change
     * to match the assignment/future API naming - no behavioral change). SQLite can't ALTER a
     * CHECK constraint in place, so this rebuilds the table: existing rows are preserved via the
     * CASE-mapped INSERT...SELECT, only the status value is rewritten. Every other status value
     * and column passes through unchanged. Table/column names describing the OLD ('stations',
     * 'station_id') schema shape are deliberately hardcoded as literals here rather than via
     * DatabaseContract, since that object no longer describes this historical shape (see
     * migrateBookingsToNodesAndSlots) - this migration must keep building the schema exactly as
     * it existed at v1/v2, independent of later contract changes.
     */
    private fun migrateBookingStatusConfirmedToApproved(db: SQLiteDatabase) {
        val b = DatabaseContract.Bookings
        val u = DatabaseContract.Users

        db.execSQL(
            """
            CREATE TABLE bookings_new (
                ${b.COL_BOOKING_ID} TEXT PRIMARY KEY,
                ${b.COL_PROSUMER_NIC} TEXT NOT NULL,
                station_id TEXT NOT NULL,
                ${b.COL_BOOKING_DATE} TEXT NOT NULL,
                ${b.COL_BOOKING_TIME} TEXT NOT NULL,
                ${b.COL_ENERGY_AMOUNT} REAL NOT NULL,
                ${b.COL_STATUS} TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK(${b.COL_STATUS} IN ('PENDING','APPROVED','CANCELLED','COMPLETED','EXPIRED')),
                ${b.COL_SYNC_STATUS} TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    CHECK(${b.COL_SYNC_STATUS} IN ('LOCAL_ONLY','PENDING_SYNC','SYNCED','SYNC_FAILED')),
                ${b.COL_CREATED_AT} INTEGER NOT NULL,
                ${b.COL_UPDATED_AT} INTEGER NOT NULL,
                FOREIGN KEY(${b.COL_PROSUMER_NIC}) REFERENCES ${u.TABLE}(${u.COL_NIC}) ON DELETE CASCADE,
                FOREIGN KEY(station_id) REFERENCES stations(station_id) ON DELETE RESTRICT
            )
            """
        )

        db.execSQL(
            """
            INSERT INTO bookings_new (
                ${b.COL_BOOKING_ID}, ${b.COL_PROSUMER_NIC}, station_id, ${b.COL_BOOKING_DATE}, ${b.COL_BOOKING_TIME},
                ${b.COL_ENERGY_AMOUNT}, ${b.COL_STATUS}, ${b.COL_SYNC_STATUS}, ${b.COL_CREATED_AT}, ${b.COL_UPDATED_AT}
            )
            SELECT
                ${b.COL_BOOKING_ID}, ${b.COL_PROSUMER_NIC}, station_id, ${b.COL_BOOKING_DATE}, ${b.COL_BOOKING_TIME},
                ${b.COL_ENERGY_AMOUNT},
                CASE ${b.COL_STATUS} WHEN 'CONFIRMED' THEN 'APPROVED' ELSE ${b.COL_STATUS} END,
                ${b.COL_SYNC_STATUS}, ${b.COL_CREATED_AT}, ${b.COL_UPDATED_AT}
            FROM ${b.TABLE}
            """
        )

        db.execSQL("DROP TABLE ${b.TABLE}")
        db.execSQL("ALTER TABLE bookings_new RENAME TO ${b.TABLE}")
        db.execSQL("CREATE INDEX idx_bookings_prosumer ON ${b.TABLE}(${b.COL_PROSUMER_NIC})")
        db.execSQL("CREATE INDEX idx_bookings_station ON ${b.TABLE}(station_id)")
        db.execSQL("CREATE INDEX idx_bookings_sync ON ${b.TABLE}(${b.COL_SYNC_STATUS})")
    }

    /**
     * v6 -> v7: replaces the fictional local-only `stations` table (a plain slot-counter model)
     * with real backend nodes/slots - `bookings.station_id` becomes `node_id`+`slot_id`,
     * individually addressable, matching the backend's actual Reservation model (see
     * CachedNodes). Every prior `bookings` row (real or seeded-demo) was created against that
     * fictional table with no backend counterpart at all - reservation/backend integration
     * simply didn't exist before this - so there is no meaningful node_id/slot_id to map them to;
     * this rebuild intentionally does NOT carry any rows forward, unlike every earlier migration
     * here. `transactions` (the QR pass table) is cleared for the same reason: its rows all
     * reference bookings that are about to disappear. Both tables end up empty, ready to be
     * populated for real via RemoteReservationRepository/RemoteTransactionRepository going forward.
     */
    private fun migrateBookingsToNodesAndSlots(db: SQLiteDatabase) {
        val b = DatabaseContract.Bookings
        val u = DatabaseContract.Users
        val t = DatabaseContract.Transactions

        db.delete(t.TABLE, null, null)
        db.execSQL("DROP TABLE ${b.TABLE}")
        db.execSQL(
            """
            CREATE TABLE ${b.TABLE} (
                ${b.COL_BOOKING_ID} TEXT PRIMARY KEY,
                ${b.COL_PROSUMER_NIC} TEXT NOT NULL,
                ${b.COL_NODE_ID} TEXT NOT NULL,
                ${b.COL_SLOT_ID} TEXT NOT NULL,
                ${b.COL_BOOKING_DATE} TEXT NOT NULL,
                ${b.COL_BOOKING_TIME} TEXT NOT NULL,
                ${b.COL_ENERGY_AMOUNT} REAL NOT NULL,
                ${b.COL_STATUS} TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK(${b.COL_STATUS} IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','EXPIRED')),
                ${b.COL_SYNC_STATUS} TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    CHECK(${b.COL_SYNC_STATUS} IN ('LOCAL_ONLY','PENDING_SYNC','SYNCED','SYNC_FAILED')),
                ${b.COL_CREATED_AT} INTEGER NOT NULL,
                ${b.COL_UPDATED_AT} INTEGER NOT NULL,
                FOREIGN KEY(${b.COL_PROSUMER_NIC}) REFERENCES ${u.TABLE}(${u.COL_NIC}) ON DELETE CASCADE
            )
            """
        )
        db.execSQL(b.CREATE_INDEX_PROSUMER)
        db.execSQL(b.CREATE_INDEX_NODE)
        db.execSQL(b.CREATE_INDEX_SYNC)
        db.execSQL("DROP TABLE IF EXISTS stations")
    }

    /**
     * v7 -> v8: widens sync_outbox.operation_type's CHECK constraint to also allow the new
     * RESERVATION_* operation types (see OutboxOperationType) now that reservation writes are
     * offline-first too. SQLite can't ALTER a CHECK constraint in place, so this rebuilds the
     * table - same technique as migrateBookingStatusConfirmedToApproved. Existing rows (only ever
     * PROSUMER_* so far) pass through unchanged.
     */
    private fun migrateSyncOutboxAddReservationOperationTypes(db: SQLiteDatabase) {
        val s = DatabaseContract.SyncOutbox

        db.execSQL(
            """
            CREATE TABLE sync_outbox_new (
                ${s.COL_ID} TEXT PRIMARY KEY,
                ${s.COL_OPERATION_TYPE} TEXT NOT NULL
                    CHECK(${s.COL_OPERATION_TYPE} IN (
                        'PROSUMER_REGISTER','PROSUMER_APPROVE','PROSUMER_DENY',
                        'RESERVATION_CREATE','RESERVATION_UPDATE','RESERVATION_CANCEL',
                        'RESERVATION_APPROVE','RESERVATION_REJECT'
                    )),
                ${s.COL_ENTITY_REF} TEXT NOT NULL,
                ${s.COL_PAYLOAD_JSON} TEXT NOT NULL,
                ${s.COL_STATUS} TEXT NOT NULL DEFAULT 'PENDING_SYNC'
                    CHECK(${s.COL_STATUS} IN ('PENDING_SYNC','SYNC_FAILED')),
                ${s.COL_ATTEMPT_COUNT} INTEGER NOT NULL DEFAULT 0,
                ${s.COL_LAST_ERROR} TEXT,
                ${s.COL_CREATED_AT} INTEGER NOT NULL,
                ${s.COL_UPDATED_AT} INTEGER NOT NULL
            )
            """
        )

        db.execSQL(
            """
            INSERT INTO sync_outbox_new (
                ${s.COL_ID}, ${s.COL_OPERATION_TYPE}, ${s.COL_ENTITY_REF}, ${s.COL_PAYLOAD_JSON},
                ${s.COL_STATUS}, ${s.COL_ATTEMPT_COUNT}, ${s.COL_LAST_ERROR}, ${s.COL_CREATED_AT}, ${s.COL_UPDATED_AT}
            )
            SELECT
                ${s.COL_ID}, ${s.COL_OPERATION_TYPE}, ${s.COL_ENTITY_REF}, ${s.COL_PAYLOAD_JSON},
                ${s.COL_STATUS}, ${s.COL_ATTEMPT_COUNT}, ${s.COL_LAST_ERROR}, ${s.COL_CREATED_AT}, ${s.COL_UPDATED_AT}
            FROM ${s.TABLE}
            """
        )

        db.execSQL("DROP TABLE ${s.TABLE}")
        db.execSQL("ALTER TABLE sync_outbox_new RENAME TO ${s.TABLE}")
        db.execSQL(s.CREATE_INDEX_STATUS)
    }

    /** Dev-only seeded account so the Prosumer flow has something to log in as out of the box. */
    private fun seedDemoProsumer(db: SQLiteDatabase) {
        val values = ContentValues().apply {
            put(DatabaseContract.Users.COL_NIC, "PR0000001")
            put(DatabaseContract.Users.COL_NAME, "Kasun Perera")
            put(DatabaseContract.Users.COL_EMAIL, "prosumer@smartsolar.test")
            put(DatabaseContract.Users.COL_PHONE, null as String?)
            put(DatabaseContract.Users.COL_ADDRESS, null as String?)
            put(DatabaseContract.Users.COL_PASSWORD_HASH, PasswordHasher.hash("Prosumer@123".toCharArray()))
            put(DatabaseContract.Users.COL_ROLE, "PROSUMER")
            put(DatabaseContract.Users.COL_ACCOUNT_STATUS, "ACTIVE")
        }
        db.insertWithOnConflict(DatabaseContract.Users.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

}
