package com.example.smart_solar_mgt_app.core.network

import org.json.JSONObject

/**
 * org.json's optString(key, null) returns the literal string "null" for an explicit JSON null
 * (JSONObject.NULL.toString()), not actual null - this is the correct way to read an optional
 * string field the sender may omit entirely or send as JSON null. Shared by
 * RemoteProsumerRepositoryImpl (parsing server responses) and SyncWorker (parsing its own
 * previously-written outbox payloads).
 */
fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else if (has(key)) getString(key) else null
