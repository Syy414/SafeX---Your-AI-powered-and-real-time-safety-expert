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

    fun stop(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}