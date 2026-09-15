package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.Stations
import com.example.smart_solar_mgt_app.domain.model.SolarStation
import com.example.smart_solar_mgt_app.domain.model.StationStatus

class StationDao {

    fun insert(db: SQLiteDatabase, station: SolarStation) {
        db.insertOrThrow(Stations.TABLE, null, station.toContentValues())
    }

    fun update(db: SQLiteDatabase, station: SolarStation) {
        db.update(Stations.TABLE, station.toContentValues(), "${Stations.COL_STATION_ID} = ?", arrayOf(station.stationId))
    }

    fun getById(db: SQLiteDatabase, stationId: String): SolarStation? {
        db.query(Stations.TABLE, null, "${Stations.COL_STATION_ID} = ?", arrayOf(stationId), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toStation() else null
        }
    }

    fun getAll(db: SQLiteDatabase): List<SolarStation> {
        db.query(Stations.TABLE, null, null, null, null, null, "${Stations.COL_STATION_NAME} ASC").use { cursor ->
            val results = mutableListOf<SolarStation>()
            while (cursor.moveToNext()) results.add(cursor.toStation())
            return results
        }
    }

    fun getAvailable(db: SQLiteDatabase): List<SolarStation> {
        val selection = "${Stations.COL_STATUS} = ? AND ${Stations.COL_AVAILABLE_SLOTS} > 0"
        db.query(Stations.TABLE, null, selection, arrayOf(StationStatus.ACTIVE.name), null, null, "${Stations.COL_STATION_NAME} ASC").use { cursor ->
            val results = mutableListOf<SolarStation>()
            while (cursor.moveToNext()) results.add(cursor.toStation())
            return results
        }
    }

    fun updateStatus(db: SQLiteDatabase, stationId: String, status: StationStatus) {
        val values = ContentValues().apply { put(Stations.COL_STATUS, status.name) }
        db.update(Stations.TABLE, values, "${Stations.COL_STATION_ID} = ?", arrayOf(stationId))
    }

    /** Adjusts available_slots by [delta] (positive to restore, negative to consume). Caller must run this inside a transaction. */
    fun adjustAvailableSlots(db: SQLiteDatabase, stationId: String, delta: Int) {
        db.execSQL(
            "UPDATE ${Stations.TABLE} SET ${Stations.COL_AVAILABLE_SLOTS} = ${Stations.COL_AVAILABLE_SLOTS} + ? WHERE ${Stations.COL_STATION_ID} = ?",
            arrayOf<Any>(delta, stationId)
        )
    }

    private fun SolarStation.toContentValues(): ContentValues = ContentValues().apply {
        put(Stations.COL_STATION_ID, stationId)
        put(Stations.COL_STATION_NAME, stationName)
        put(Stations.COL_LATITUDE, latitude)
        put(Stations.COL_LONGITUDE, longitude)
        put(Stations.COL_CAPACITY_KWH, capacityKwh)
        put(Stations.COL_AVAILABLE_SLOTS, availableSlots)
        put(Stations.COL_STATUS, status.name)
    }

    private fun Cursor.toStation(): SolarStation = SolarStation(
        stationId = getString(getColumnIndexOrThrow(Stations.COL_STATION_ID)),
        stationName = getString(getColumnIndexOrThrow(Stations.COL_STATION_NAME)),
        latitude = getDouble(getColumnIndexOrThrow(Stations.COL_LATITUDE)),
        longitude = getDouble(getColumnIndexOrThrow(Stations.COL_LONGITUDE)),
        capacityKwh = getDouble(getColumnIndexOrThrow(Stations.COL_CAPACITY_KWH)),
        availableSlots = getInt(getColumnIndexOrThrow(Stations.COL_AVAILABLE_SLOTS)),
        status = StationStatus.valueOf(getString(getColumnIndexOrThrow(Stations.COL_STATUS)))
    )
}