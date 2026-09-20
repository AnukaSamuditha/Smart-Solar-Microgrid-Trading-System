package com.example.smart_solar_mgt_app.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class SyncManagerImpl(private val context: Context) : SyncManager {

    override fun scheduleImmediateSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(SYNC_CONSTRAINTS)
            .build()
        // KEEP, not REPLACE: if a sync is already queued/running, this call just means "there's
        // now more work for it to pick up" - no need for a second run back-to-back.
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }

    override fun schedulePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(SYNC_CONSTRAINTS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val IMMEDIATE_WORK_NAME = "sync_outbox_immediate"
        const val PERIODIC_WORK_NAME = "sync_outbox_periodic"
        const val PERIODIC_INTERVAL_MINUTES = 15L
        val SYNC_CONSTRAINTS: Constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
