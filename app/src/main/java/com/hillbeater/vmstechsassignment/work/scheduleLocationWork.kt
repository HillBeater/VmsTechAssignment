package com.hillbeater.vmstechsassignment.work

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.*
import com.hillbeater.vmstechsassignment.service.LocationForegroundService
import java.util.concurrent.TimeUnit

fun scheduleLocationWork(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
        .setRequiresBatteryNotLow(true)
        .setRequiresCharging(false)
        .build()

    val serviceIntent = Intent(context, LocationForegroundService::class.java)
    ContextCompat.startForegroundService(context, serviceIntent)

    val locationWorkRequest = PeriodicWorkRequestBuilder<LocationWorker>(
        15, TimeUnit.MINUTES
    )
        .setConstraints(constraints)
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "LocationWork",
        ExistingPeriodicWorkPolicy.REPLACE,
        locationWorkRequest
    )
}
