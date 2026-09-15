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
        db.setForeignKeyConstraintsEnabled(true)
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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migrations yet - DATABASE_VERSION is still 1. Future schema changes land here as
        // incremental `if (oldVersion < N)` steps, never a single drop-and-recreate.
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