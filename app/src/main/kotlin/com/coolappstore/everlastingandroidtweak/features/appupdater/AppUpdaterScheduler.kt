package com.coolappstore.everlastingandroidtweak.features.appupdater

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object AppUpdaterScheduler {

    private const val WORK_NAME = "everlasting_app_updater_periodic"

    /** intervalMinutes == 0 cancels the schedule */
    fun schedule(context: Context, intervalMinutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (intervalMinutes <= 0) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<AppUpdaterWorker>(
            intervalMinutes.toLong(), TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
            .build()

        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
