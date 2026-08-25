package com.polarisrh.tabletpolaris

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.polarisrh.tabletpolaris.work.HeartbeatWorker
import com.polarisrh.tabletpolaris.work.PunchSyncWorker
import java.util.concurrent.TimeUnit

class PolarisApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scheduleHeartbeat()
        schedulePunchSync()
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

    /**
     * Rede de segurança pra fila offline de batidas — o caminho comum é a sincronização
     * imediata disparada logo após cada ponto (ver RoomPunchRepository), mas isso cobre o caso
     * de a rede voltar sem nenhuma batida nova acontecer nesse meio-tempo.
     */
    private fun schedulePunchSync() {
        val request = PeriodicWorkRequestBuilder<PunchSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PunchSyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private companion object {
        const val HEARTBEAT_WORK_NAME = "device_heartbeat"
    }
}
