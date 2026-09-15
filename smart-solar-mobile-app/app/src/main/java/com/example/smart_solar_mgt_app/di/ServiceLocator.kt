package com.example.smart_solar_mgt_app.di

import android.content.Context
import com.example.smart_solar_mgt_app.core.db.LocalDbManager

/**
 * Manual dependency container. Managers/repositories are added here as each
 * architectural piece is implemented (SecurityManager, CommunicationManager, etc.).
 */
object ServiceLocator {

    private lateinit var appContext: Context

    val localDbManager: LocalDbManager by lazy { LocalDbManager(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun applicationContext(): Context = appContext
}