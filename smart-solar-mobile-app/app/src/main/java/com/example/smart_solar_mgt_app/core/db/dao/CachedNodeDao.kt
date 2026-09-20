package com.example.smart_solar_mgt_app.core.db.dao

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.smart_solar_mgt_app.core.db.DatabaseContract.CachedNodes
import com.example.smart_solar_mgt_app.domain.model.BatterySlot
import com.example.smart_solar_mgt_app.domain.model.BatterySlotStatus
import com.example.smart_solar_mgt_app.domain.model.MicrogridNode
import com.example.smart_solar_mgt_app.domain.model.NodeStatus
import org.json.JSONArray
import org.json.JSONObject

class CachedNodeDao {

    fun upsert(db: SQLiteDatabase, node: MicrogridNode, syncedAt: Long) {
        db.insertWithOnConflict(CachedNodes.TABLE, null, node.toContentValues(syncedAt), SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getById(db: SQLiteDatabase, nodeId: String): MicrogridNode? {
        db.query(CachedNodes.TABLE, null, "${CachedNodes.COL_NODE_ID} = ?", arrayOf(nodeId), null, null, null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.toNode() else null
        }
    }

    fun getAll(db: SQLiteDatabase): List<MicrogridNode> {
        db.query(CachedNodes.TABLE, null, null, null, null, null, "${CachedNodes.COL_NAME} ASC").use { cursor ->
            val results = mutableListOf<MicrogridNode>()
            while (cursor.moveToNext()) results.add(cursor.toNode())
            return results
        }
    }

    /** Caller must run this inside a transaction alongside the upserts that follow it - see LocalDbManager.replaceCachedNodes. */
    fun deleteAll(db: SQLiteDatabase) {
        db.delete(CachedNodes.TABLE, null, null)
    }

    private fun MicrogridNode.toContentValues(syncedAt: Long): ContentValues {
        val slotsJson = JSONArray()
        for (slot in batterySlots) {
            slotsJson.put(JSONObject().put("slotId", slot.slotId).put("status", slot.status.name))
        }
        return ContentValues().apply {
            put(CachedNodes.COL_NODE_ID, nodeId)
            put(CachedNodes.COL_NAME, name)
            put(CachedNodes.COL_LATITUDE, latitude)
            put(CachedNodes.COL_LONGITUDE, longitude)
            put(CachedNodes.COL_CAPACITY_KW, capacityKw)
            put(CachedNodes.COL_STATUS, status.name)
            put(CachedNodes.COL_BATTERY_SLOTS_JSON, slotsJson.toString())
            put(CachedNodes.COL_LAST_SYNCED_AT, syncedAt)
        }
    }

    private fun Cursor.toNode(): MicrogridNode {
        val slotsJson = JSONArray(getString(getColumnIndexOrThrow(CachedNodes.COL_BATTERY_SLOTS_JSON)))
        val slots = (0 until slotsJson.length()).map { index ->
            val slot = slotsJson.getJSONObject(index)
            BatterySlot(slotId = slot.getString("slotId"), status = BatterySlotStatus.valueOf(slot.getString("status")))
        }
        return MicrogridNode(
            nodeId = getString(getColumnIndexOrThrow(CachedNodes.COL_NODE_ID)),
            name = getString(getColumnIndexOrThrow(CachedNodes.COL_NAME)),
            latitude = getDouble(getColumnIndexOrThrow(CachedNodes.COL_LATITUDE)),
            longitude = getDouble(getColumnIndexOrThrow(CachedNodes.COL_LONGITUDE)),
            capacityKw = getDouble(getColumnIndexOrThrow(CachedNodes.COL_CAPACITY_KW)),
            batterySlots = slots,
            status = NodeStatus.valueOf(getString(getColumnIndexOrThrow(CachedNodes.COL_STATUS)))
        )
    }
}
