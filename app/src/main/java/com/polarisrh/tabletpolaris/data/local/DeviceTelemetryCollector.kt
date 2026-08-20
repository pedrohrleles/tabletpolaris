package com.polarisrh.tabletpolaris.data.local

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager

data class DeviceTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean
)

/** Reads live device telemetry (battery) — used both at activation and in the periodic heartbeat. */
class DeviceTelemetryCollector(private val context: Context) {

    fun collect(): DeviceTelemetry {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return DeviceTelemetry(
            batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = isDeviceCharging()
        )
    }

    /**
     * BatteryManager.isCharging() reflete o estado interno do chip de carga, que em vários
     * aparelhos reporta false com a bateria cheia mesmo com o cabo conectado. Checar
     * EXTRA_PLUGGED (fonte de energia conectada) é mais confiável pra "está carregando".
     */
    private fun isDeviceCharging(): Boolean {
        val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        return plugged != 0
    }
}
