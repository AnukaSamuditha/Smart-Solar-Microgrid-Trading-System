package com.example.smart_solar_mgt_app.core.db

/**
 * Table and column names for the local SQLite schema, plus the DDL used to create it.
 * Keeping these in one place avoids stringly-typed literals scattered across the DAOs.
 */
object DatabaseContract {

    const val DATABASE_NAME = "smart_solar.db"
    const val DATABASE_VERSION = 8

    // password_hash is nullable (v4->v5 migration) - a self-registered prosumer has a local row
    // with no password until it's approved and they set one via ResetPasswordActivity; see
    // SecurityManagerImpl.login, which always defers to the remote endpoint for such a row.
    object Users {
        const val TABLE = "users"
        const val COL_NIC = "nic"
        const val COL_NAME = "name"
        const val COL_EMAIL = "email"
        const val COL_PHONE = "phone"
        const val COL_ADDRESS = "address"
        const val COL_PASSWORD_HASH = "password_hash"
        const val COL_ROLE = "role"
        const val COL_ACCOUNT_STATUS = "account_status"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_NIC TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_EMAIL TEXT NOT NULL UNIQUE,
                $COL_PHONE TEXT,
                $COL_ADDRESS TEXT,
                $COL_PASSWORD_HASH TEXT,
                $COL_ROLE TEXT NOT NULL CHECK($COL_ROLE IN ('PROSUMER','GRID_OPERATOR')),
                $COL_ACCOUNT_STATUS TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK($COL_ACCOUNT_STATUS IN (
                        'PENDING_APPROVAL','ACTIVE','SUSPENDED','REJECTED',
                        'DEACTIVATION_REQUESTED','DEACTIVATED'
                    ))
            )
        """
    }

    // v6->v7: station_id (plain slot-counter model) is replaced by node_id+slot_id (individually
    // addressable battery slots, matching the backend's real Reservation model exactly - see
    // CachedNodes below). No local FK on either, matching the backend's own precedent of plain,
    // unvalidated foreign-key strings for Reservation.NodeId/SlotId - the node cache is a display
    // cache, not a referential-integrity source. REJECTED is a new status: the backend now
    // distinguishes a staff rejection from a prosumer's own cancellation.
    object Bookings {
        const val TABLE = "bookings"
        const val COL_BOOKING_ID = "booking_id"
        const val COL_PROSUMER_NIC = "prosumer_nic"
        const val COL_NODE_ID = "node_id"
        const val COL_SLOT_ID = "slot_id"
        const val COL_BOOKING_DATE = "booking_date"
        const val COL_BOOKING_TIME = "booking_time"
        const val COL_ENERGY_AMOUNT = "energy_amount"
        const val COL_STATUS = "status"
        const val COL_SYNC_STATUS = "sync_status"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_BOOKING_ID TEXT PRIMARY KEY,
                $COL_PROSUMER_NIC TEXT NOT NULL,
                $COL_NODE_ID TEXT NOT NULL,
                $COL_SLOT_ID TEXT NOT NULL,
                $COL_BOOKING_DATE TEXT NOT NULL,
                $COL_BOOKING_TIME TEXT NOT NULL,
                $COL_ENERGY_AMOUNT REAL NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK($COL_STATUS IN ('PENDING','APPROVED','REJECTED','CANCELLED','COMPLETED','EXPIRED')),
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    CHECK($COL_SYNC_STATUS IN ('LOCAL_ONLY','PENDING_SYNC','SYNCED','SYNC_FAILED')),
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_PROSUMER_NIC) REFERENCES ${Users.TABLE}(${Users.COL_NIC}) ON DELETE CASCADE
            )
        """

        const val CREATE_INDEX_PROSUMER = "CREATE INDEX idx_bookings_prosumer ON $TABLE($COL_PROSUMER_NIC)"
        const val CREATE_INDEX_NODE = "CREATE INDEX idx_bookings_node ON $TABLE($COL_NODE_ID)"
        const val CREATE_INDEX_SYNC = "CREATE INDEX idx_bookings_sync ON $TABLE($COL_SYNC_STATUS)"
    }

    object Transactions {
        const val TABLE = "transactions"
        const val COL_TRANSACTION_ID = "transaction_id"
        const val COL_BOOKING_ID = "booking_id"
        const val COL_QR_TOKEN = "qr_token"
        const val COL_STATUS = "status"
        const val COL_OPERATOR_ID = "operator_id"
        const val COL_GENERATED_AT = "generated_at"
        const val COL_COMPLETED_AT = "completed_at"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_TRANSACTION_ID TEXT PRIMARY KEY,
                $COL_BOOKING_ID TEXT NOT NULL,
                $COL_QR_TOKEN TEXT NOT NULL UNIQUE,
                $COL_STATUS TEXT NOT NULL DEFAULT 'GENERATED'
                    CHECK($COL_STATUS IN ('GENERATED','SCANNED','COMPLETED','EXPIRED','CANCELLED')),
                $COL_OPERATOR_ID TEXT,
                $COL_GENERATED_AT INTEGER NOT NULL,
                $COL_COMPLETED_AT INTEGER,
                FOREIGN KEY($COL_BOOKING_ID) REFERENCES ${Bookings.TABLE}(${Bookings.COL_BOOKING_ID}) ON DELETE CASCADE,
                FOREIGN KEY($COL_OPERATOR_ID) REFERENCES ${Users.TABLE}(${Users.COL_NIC}) ON DELETE SET NULL
            )
        """

        const val CREATE_INDEX_BOOKING = "CREATE INDEX idx_transactions_booking ON $TABLE($COL_BOOKING_ID)"
    }

    /**
     * Generic outbox for backend operations queued while offline - built for prosumer
     * registration/approve/deny (core/sync/SyncManager.kt) but deliberately not specific to
     * those, so a future offline-writable operation (e.g. bookings) can reuse it. A row's mere
     * existence means "not yet confirmed by the server"; SyncWorker deletes it on success and
     * marks it SYNC_FAILED (kept, not retried) on a definitive server rejection.
     */
    object SyncOutbox {
        const val TABLE = "sync_outbox"
        const val COL_ID = "id"
        const val COL_OPERATION_TYPE = "operation_type"
        const val COL_ENTITY_REF = "entity_ref"
        const val COL_PAYLOAD_JSON = "payload_json"
        const val COL_STATUS = "status"
        const val COL_ATTEMPT_COUNT = "attempt_count"
        const val COL_LAST_ERROR = "last_error"
        const val COL_CREATED_AT = "created_at"
        const val COL_UPDATED_AT = "updated_at"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_ID TEXT PRIMARY KEY,
                $COL_OPERATION_TYPE TEXT NOT NULL
                    CHECK($COL_OPERATION_TYPE IN (
                        'PROSUMER_REGISTER','PROSUMER_APPROVE','PROSUMER_DENY',
                        'RESERVATION_CREATE','RESERVATION_UPDATE','RESERVATION_CANCEL',
                        'RESERVATION_APPROVE','RESERVATION_REJECT'
                    )),
                $COL_ENTITY_REF TEXT NOT NULL,
                $COL_PAYLOAD_JSON TEXT NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT 'PENDING_SYNC'
                    CHECK($COL_STATUS IN ('PENDING_SYNC','SYNC_FAILED')),
                $COL_ATTEMPT_COUNT INTEGER NOT NULL DEFAULT 0,
                $COL_LAST_ERROR TEXT,
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL
            )
        """

        const val CREATE_INDEX_STATUS = "CREATE INDEX idx_sync_outbox_status ON $TABLE($COL_STATUS)"
    }

    /**
     * Read-through cache of prosumer-browsable microgrid nodes (GET /api/v1/nodes/mine), so the
     * node/slot picker still works offline with the last-known list (see
     * RemoteNodeRepository/CachedNodeDao). A full replace-on-refresh cache, not incrementally
     * synced - node/slot data is small, read-only from this device's perspective, and has no
     * local writes of its own to reconcile. battery_slots_json is a denormalized JSON blob
     * (`[{"slotId":..,"status":..}]`) rather than a child table, since slots are never queried or
     * written independently on this device.
     */
    object CachedNodes {
        const val TABLE = "cached_nodes"
        const val COL_NODE_ID = "node_id"
        const val COL_NAME = "name"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_CAPACITY_KW = "capacity_kw"
        const val COL_STATUS = "status"
        const val COL_BATTERY_SLOTS_JSON = "battery_slots_json"
        const val COL_LAST_SYNCED_AT = "last_synced_at"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_NODE_ID TEXT PRIMARY KEY,
                $COL_NAME TEXT NOT NULL,
                $COL_LATITUDE REAL NOT NULL,
                $COL_LONGITUDE REAL NOT NULL,
                $COL_CAPACITY_KW REAL NOT NULL,
                $COL_STATUS TEXT NOT NULL CHECK($COL_STATUS IN ('ACTIVE','DEACTIVATED')),
                $COL_BATTERY_SLOTS_JSON TEXT NOT NULL,
                $COL_LAST_SYNCED_AT INTEGER NOT NULL
            )
        """
    }

    object SessionTable {
        const val TABLE = "session"
        const val COL_USER_ID = "user_id"
        const val COL_ROLE = "role"
        const val COL_LOGIN_STATE = "login_state"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_USER_ID TEXT PRIMARY KEY,
                $COL_ROLE TEXT NOT NULL,
                $COL_LOGIN_STATE TEXT NOT NULL CHECK($COL_LOGIN_STATE IN ('LOGGED_IN','LOGGED_OUT'))
            )
        """
    }
}