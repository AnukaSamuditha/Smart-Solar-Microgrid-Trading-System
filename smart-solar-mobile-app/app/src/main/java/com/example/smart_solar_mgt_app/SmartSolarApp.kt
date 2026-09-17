package com.example.smart_solar_mgt_app

import android.app.Application
import com.example.smart_solar_mgt_app.di.ServiceLocator

class SmartSolarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }
}