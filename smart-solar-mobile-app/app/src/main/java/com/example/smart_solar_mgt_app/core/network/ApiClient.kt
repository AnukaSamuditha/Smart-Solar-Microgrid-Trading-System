package com.example.smart_solar_mgt_app.core.network

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Single shared OkHttp client for calls to the Web Service. Deliberately plain OkHttp (no
 * Retrofit/Moshi) to match this project's preference for raw, manual implementations over
 * added framework layers (see smart-solar-mobile-app/CLAUDE.md - "No Room, no ... Hilt").
 */
object ApiClient {
    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
