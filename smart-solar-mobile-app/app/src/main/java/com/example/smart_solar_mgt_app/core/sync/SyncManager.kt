package com.example.smart_solar_mgt_app.core.sync

/**
 * Drains the local sync outbox (core/db/DatabaseContract.SyncOutbox) against the real backend.
 * The long-designed-but-never-built piece referenced in di/ServiceLocator.kt's original comment
 * ("CommunicationManager, SyncManager, etc.") - see SyncWorker for the actual sync logic.
 */
interface SyncManager {
    /** Enqueues a one-off sync attempt now - runs as soon as connectivity allows, which in the
     * common case (already online) is effectively immediately. Call this right after queuing an
     * outbox operation so the online path doesn't have to wait for the periodic safety net. */
    fun scheduleImmediateSync()

    /** Ensures the recurring safety-net sync is scheduled - call once at app startup
     * (SmartSolarApp.onCreate). Idempotent: re-scheduling an already-scheduled periodic worker
     * (same unique work name) is a no-op. */
    fun schedulePeriodicSync()
}
