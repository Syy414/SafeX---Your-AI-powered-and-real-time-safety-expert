package com.safex.app.guardian

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object GalleryScanWork {
    private const val UNIQUE_WORK_NAME = "safex_gallery_scan"

    fun startPeriodic(context: Context) {
        // Periodic work minimum is 15 minutes (Android/WorkManager rule)
        val request = PeriodicWorkRequestBuilder<ScanTextWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runOnceNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<ScanTextWorker>().build()
        WorkManager.getInstance(context).enqueue(req)
    }

    /**
     * Schedules a one-time scan but only if one isn't already pending/running.
     * Use this when triggered by gallery observer to avoid scan pile-up.
     */
    fun runOnceUnique(context: Context) {
        val req = OneTimeWorkRequestBuilder<ScanTextWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "safex_gallery_scan_instant",
            androidx.work.ExistingWorkPolicy.KEEP,
            req
        )
    }

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}