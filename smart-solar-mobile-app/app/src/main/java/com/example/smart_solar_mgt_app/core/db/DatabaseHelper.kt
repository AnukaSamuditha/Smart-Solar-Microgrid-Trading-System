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
        db.execSQL(DatabaseContract.Stations.CREATE_TABLE)
        db.execSQL(DatabaseContract.Bookings.CREATE_TABLE)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_PROSUMER)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_STATION)
        db.execSQL(DatabaseContract.Bookings.CREATE_INDEX_SYNC)
        db.execSQL(DatabaseContract.Transactions.CREATE_TABLE)
        db.execSQL(DatabaseContract.Transactions.CREATE_INDEX_BOOKING)
        db.execSQL(DatabaseContract.SessionTable.CREATE_TABLE)

        seedStations(db)
        seedGridOperator(db)
        seedDemoProsumer(db)
        seedDemoBookings(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            migrateBookingStatusConfirmedToApproved(db)
        }
        if (oldVersion < 3) {
            // Adds the demo prosumer + sample bookings to installs that already exist on-device
            // (not just fresh ones via onCreate) - CONFLICT_IGNORE in both seed functions makes
            // this safe to run against a database that already has this data, or where the
            // fixed demo NIC happens to collide with something a real registration created.
            seedDemoProsumer(db)
            seedDemoBookings(db)
        }
        // Future schema changes land here as further incremental `if (oldVersion < N)` steps,
        // never a single drop-and-recreate of the whole database.
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
     * and column passes through unchanged.
     */
    private fun migrateBookingStatusConfirmedToApproved(db: SQLiteDatabase) {
        val b = DatabaseContract.Bookings
        val u = DatabaseContract.Users
        val s = DatabaseContract.Stations

        db.execSQL(
            """
            CREATE TABLE bookings_new (
                ${b.COL_BOOKING_ID} TEXT PRIMARY KEY,
                ${b.COL_PROSUMER_NIC} TEXT NOT NULL,
                ${b.COL_STATION_ID} TEXT NOT NULL,
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
                FOREIGN KEY(${b.COL_STATION_ID}) REFERENCES ${s.TABLE}(${s.COL_STATION_ID}) ON DELETE RESTRICT
            )
            """
        )

        db.execSQL(
            """
            INSERT INTO bookings_new (
                ${b.COL_BOOKING_ID}, ${b.COL_PROSUMER_NIC}, ${b.COL_STATION_ID}, ${b.COL_BOOKING_DATE}, ${b.COL_BOOKING_TIME},
                ${b.COL_ENERGY_AMOUNT}, ${b.COL_STATUS}, ${b.COL_SYNC_STATUS}, ${b.COL_CREATED_AT}, ${b.COL_UPDATED_AT}
            )
            SELECT
                ${b.COL_BOOKING_ID}, ${b.COL_PROSUMER_NIC}, ${b.COL_STATION_ID}, ${b.COL_BOOKING_DATE}, ${b.COL_BOOKING_TIME},
                ${b.COL_ENERGY_AMOUNT},
                CASE ${b.COL_STATUS} WHEN 'CONFIRMED' THEN 'APPROVED' ELSE ${b.COL_STATUS} END,
                ${b.COL_SYNC_STATUS}, ${b.COL_CREATED_AT}, ${b.COL_UPDATED_AT}
            FROM ${b.TABLE}
            """
        )

        db.execSQL("DROP TABLE ${b.TABLE}")
        db.execSQL("ALTER TABLE bookings_new RENAME TO ${b.TABLE}")
        db.execSQL(b.CREATE_INDEX_PROSUMER)
        db.execSQL(b.CREATE_INDEX_STATION)
        db.execSQL(b.CREATE_INDEX_SYNC)
    }

    private fun seedStations(db: SQLiteDatabase) {
        val seedStations = listOf(
            SeedStation("station-001", "Colombo Fort Solar Hub", 6.9344, 79.8428, 50.0, 4, "ACTIVE"),
            SeedStation("station-002", "Kandy Hillside Array", 7.2906, 80.6337, 30.0, 0, "FULL"),
            SeedStation("station-003", "Galle Coastal Station", 6.0535, 80.2210, 40.0, 6, "ACTIVE"),
            SeedStation("station-004", "Jaffna Northern Grid", 9.6615, 80.0255, 25.0, 3, "MAINTENANCE"),
            SeedStation("station-005", "Kurunegala Central Point", 7.4863, 80.3647, 35.0, 5, "ACTIVE")
        )
        for (station in seedStations) {
            val values = ContentValues().apply {
                put(DatabaseContract.Stations.COL_STATION_ID, station.id)
                put(DatabaseContract.Stations.COL_STATION_NAME, station.name)
                put(DatabaseContract.Stations.COL_LATITUDE, station.lat)
                put(DatabaseContract.Stations.COL_LONGITUDE, station.lng)
                put(DatabaseContract.Stations.COL_CAPACITY_KWH, station.capacityKwh)
                put(DatabaseContract.Stations.COL_AVAILABLE_SLOTS, station.availableSlots)
                put(DatabaseContract.Stations.COL_STATUS, station.status)
            }
            db.insert(DatabaseContract.Stations.TABLE, null, values)
        }
    }

    /** Dev-only seeded account so the Grid Operator flow is testable before a registration/backoffice flow exists. */
    private fun seedGridOperator(db: SQLiteDatabase) {
        val values = ContentValues().apply {
            put(DatabaseContract.Users.COL_NIC, "OP0000001")
            put(DatabaseContract.Users.COL_NAME, "Grid Operator One")
            put(DatabaseContract.Users.COL_EMAIL, "operator@smartsolar.test")
            put(DatabaseContract.Users.COL_PHONE, null as String?)
            put(DatabaseContract.Users.COL_ADDRESS, null as String?)
            put(DatabaseContract.Users.COL_PASSWORD_HASH, PasswordHasher.hash("Operator@123".toCharArray()))
            put(DatabaseContract.Users.COL_ROLE, "GRID_OPERATOR")
            put(DatabaseContract.Users.COL_ACCOUNT_STATUS, "ACTIVE")
        }
        db.insert(DatabaseContract.Users.TABLE, null, values)
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

    /**
     * A spread of bookings across every status for the demo prosumer, so every screen has
     * something to show right after install instead of empty states everywhere: two PENDING
     * (left for the Grid Operator demo account to actually approve/reject, exercising that real
     * flow rather than faking an already-APPROVED booking with no genuine signed QR transaction
     * behind it), one COMPLETED and one CANCELLED for the History tab.
     */
    private fun seedDemoBookings(db: SQLiteDatabase) {
        val today = java.time.LocalDate.now()
        val seedBookings = listOf(
            SeedBooking("seed-booking-001", "station-001", today.plusDays(1).toString(), "09:00", 10.0, "PENDING"),
            SeedBooking("seed-booking-002", "station-003", today.plusDays(2).toString(), "14:00", 12.0, "PENDING"),
            SeedBooking("seed-booking-003", "station-005", today.minusDays(3).toString(), "11:00", 20.0, "COMPLETED"),
            SeedBooking("seed-booking-004", "station-002", today.minusDays(5).toString(), "16:00", 8.0, "CANCELLED")
        )
        val now = System.currentTimeMillis()
        for (booking in seedBookings) {
            val values = ContentValues().apply {
                put(DatabaseContract.Bookings.COL_BOOKING_ID, booking.id)
                put(DatabaseContract.Bookings.COL_PROSUMER_NIC, "PR0000001")
                put(DatabaseContract.Bookings.COL_STATION_ID, booking.stationId)
                put(DatabaseContract.Bookings.COL_BOOKING_DATE, booking.date)
                put(DatabaseContract.Bookings.COL_BOOKING_TIME, booking.time)
                put(DatabaseContract.Bookings.COL_ENERGY_AMOUNT, booking.energyAmount)
                put(DatabaseContract.Bookings.COL_STATUS, booking.status)
                put(DatabaseContract.Bookings.COL_SYNC_STATUS, "SYNCED")
                put(DatabaseContract.Bookings.COL_CREATED_AT, now)
                put(DatabaseContract.Bookings.COL_UPDATED_AT, now)
            }
            db.insertWithOnConflict(DatabaseContract.Bookings.TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private data class SeedBooking(
        val id: String,
        val stationId: String,
        val date: String,
        val time: String,
        val energyAmount: Double,
        val status: String
    )

    private data class SeedStation(
        val id: String,
        val name: String,
        val lat: Double,
        val lng: Double,
        val capacityKwh: Double,
        val availableSlots: Int,
        val status: String
    )
}
