package com.polarisrh.tabletpolaris.data.local

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import kotlin.math.roundToLong

data class DeviceTelemetry(
    val batteryPercent: Int,
    val isCharging: Boolean,
    /** "13gb de 103gb" (usado de total) — formato varchar combinado com o backend, mesmo
     *  padrão da coluna de bateria em rep_core_coletor_dispositivo. */
    val armazenamento: String,
    val memoriaRam: String
)

/** Reads live device telemetry (battery, armazenamento, RAM) — used both at activation and in
 *  the periodic heartbeat. */
class DeviceTelemetryCollector(private val context: Context) {

    fun collect(): DeviceTelemetry {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return DeviceTelemetry(
            batteryPercent = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = isDeviceCharging(),
            armazenamento = coletarArmazenamento(),
            memoriaRam = coletarMemoriaRam()
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

    /** Usado/total da partição de dados — mesma fonte que Configurações > Armazenamento do
     *  Android usa. Sem permissão nenhuma, diferente do número de série. */
    private fun coletarArmazenamento(): String {
        val diretorio = Environment.getDataDirectory()
        val usado = diretorio.totalSpace - diretorio.freeSpace
        return "${formatarGb(usado)}gb de ${formatarGb(diretorio.totalSpace)}gb"
    }

    /** RAM usada/total do aparelho — totalMem é a memória física real, geralmente um pouco
     *  menor que o valor "de fábrica" anunciado (parte fica reservada pro sistema/hardware
     *  antes de qualquer app rodar). */
    private fun coletarMemoriaRam(): String {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usada = memInfo.totalMem - memInfo.availMem
        return "${formatarGb(usada)}gb de ${formatarGb(memInfo.totalMem)}gb"
    }

    private fun formatarGb(bytes: Long): Long = (bytes / (1024.0 * 1024.0 * 1024.0)).roundToLong()
}
