package com.example.smart_solar_mgt_app.core.db

/**
 * Table and column names for the local SQLite schema, plus the DDL used to create it.
 * Keeping these in one place avoids stringly-typed literals scattered across the DAOs.
 */
object DatabaseContract {

    const val DATABASE_NAME = "smart_solar.db"
    const val DATABASE_VERSION = 2

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
                $COL_PASSWORD_HASH TEXT NOT NULL,
                $COL_ROLE TEXT NOT NULL CHECK($COL_ROLE IN ('PROSUMER','GRID_OPERATOR')),
                $COL_ACCOUNT_STATUS TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK($COL_ACCOUNT_STATUS IN (
                        'PENDING_APPROVAL','ACTIVE','SUSPENDED','REJECTED',
                        'DEACTIVATION_REQUESTED','DEACTIVATED'
                    ))
            )
        """
    }

    object Stations {
        const val TABLE = "stations"
        const val COL_STATION_ID = "station_id"
        const val COL_STATION_NAME = "station_name"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_CAPACITY_KWH = "capacity_kwh"
        const val COL_AVAILABLE_SLOTS = "available_slots"
        const val COL_STATUS = "status"

        const val CREATE_TABLE = """
            CREATE TABLE $TABLE (
                $COL_STATION_ID TEXT PRIMARY KEY,
                $COL_STATION_NAME TEXT NOT NULL,
                $COL_LATITUDE REAL NOT NULL,
                $COL_LONGITUDE REAL NOT NULL,
                $COL_CAPACITY_KWH REAL NOT NULL,
                $COL_AVAILABLE_SLOTS INTEGER NOT NULL DEFAULT 0,
                $COL_STATUS TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK($COL_STATUS IN ('ACTIVE','MAINTENANCE','OFFLINE','FULL'))
            )
        """
    }

    object Bookings {
        const val TABLE = "bookings"
        const val COL_BOOKING_ID = "booking_id"
        const val COL_PROSUMER_NIC = "prosumer_nic"
        const val COL_STATION_ID = "station_id"
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
                $COL_STATION_ID TEXT NOT NULL,
                $COL_BOOKING_DATE TEXT NOT NULL,
                $COL_BOOKING_TIME TEXT NOT NULL,
                $COL_ENERGY_AMOUNT REAL NOT NULL,
                $COL_STATUS TEXT NOT NULL DEFAULT 'PENDING'
                    CHECK($COL_STATUS IN ('PENDING','APPROVED','CANCELLED','COMPLETED','EXPIRED')),
                $COL_SYNC_STATUS TEXT NOT NULL DEFAULT 'LOCAL_ONLY'
                    CHECK($COL_SYNC_STATUS IN ('LOCAL_ONLY','PENDING_SYNC','SYNCED','SYNC_FAILED')),
                $COL_CREATED_AT INTEGER NOT NULL,
                $COL_UPDATED_AT INTEGER NOT NULL,
                FOREIGN KEY($COL_PROSUMER_NIC) REFERENCES ${Users.TABLE}(${Users.COL_NIC}) ON DELETE CASCADE,
                FOREIGN KEY($COL_STATION_ID) REFERENCES ${Stations.TABLE}(${Stations.COL_STATION_ID}) ON DELETE RESTRICT
            )
        """

        const val CREATE_INDEX_PROSUMER = "CREATE INDEX idx_bookings_prosumer ON $TABLE($COL_PROSUMER_NIC)"
        const val CREATE_INDEX_STATION = "CREATE INDEX idx_bookings_station ON $TABLE($COL_STATION_ID)"
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