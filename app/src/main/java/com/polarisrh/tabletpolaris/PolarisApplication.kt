package com.polarisrh.tabletpolaris

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.polarisrh.tabletpolaris.work.HeartbeatWorker
import java.util.concurrent.TimeUnit

class PolarisApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleHeartbeat()
    }

    /**
     * Registered once; ExistingPeriodicWorkPolicy.KEEP means later app launches don't reset
     * the schedule — WorkManager keeps running this on its own even if the app isn't open,
     * and re-arms itself automatically after a device reboot.
     */
    private fun scheduleHeartbeat() {
        val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            HEARTBEAT_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val HEARTBEAT_WORK_NAME = "device_heartbeat"
    }
}
