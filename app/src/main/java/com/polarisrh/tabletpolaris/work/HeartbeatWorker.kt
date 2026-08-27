package com.polarisrh.tabletpolaris.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polarisrh.tabletpolaris.BuildConfig
import com.polarisrh.tabletpolaris.PolarisApplication
import com.polarisrh.tabletpolaris.data.local.DeviceIdentity
import com.polarisrh.tabletpolaris.data.local.DeviceKeyManager
import com.polarisrh.tabletpolaris.data.local.DeviceTelemetryCollector
import com.polarisrh.tabletpolaris.data.remote.dto.HeartbeatRequest
import java.io.IOException
import java.time.Instant

/**
 * Periodic background task (scheduled via WorkManager — runs regardless of whether the
 * app UI is open) that reports live device status, so the backend's view of this tablet
 * doesn't go stale between app launches.
 */
class HeartbeatWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val deviceKeyManager = DeviceKeyManager()

    override suspend fun doWork(): Result {
        val container = (applicationContext as PolarisApplication).container
        val credentials = container.credentialsStore.read()
            ?: return Result.success() // tablet not activated yet — nothing to report

        val telemetry = DeviceTelemetryCollector(applicationContext).collect()
        val request = HeartbeatRequest(
            nrFilaPendente = container.batidaDao.contarPendentes(),
            nrBateriaPct = telemetry.batteryPercent,
            flCarregando = telemetry.isCharging,
            armazenamento = telemetry.armazenamento,
            memRam = telemetry.memoriaRam,
            dsVersaoApp = BuildConfig.VERSION_NAME,
            dsAndroidId = DeviceIdentity.androidId(applicationContext),
            // Mesma limitação de plataforma da ativação — ver DeviceIdentity/nota na ativação.
            nrSerieDispositivo = "UNKNOWN",
            flStrongbox = deviceKeyManager.isStrongBoxBacked(),
            dtDispositivo = Instant.now().toString()
        )

        return try {
            val response = container.polarisApiService.enviarHeartbeat(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}",
                request = request
            )
            val body = response.body()

            if (response.isSuccessful && body != null) {
                when {
                    body.desativacao != null -> container.desativacaoHandler.processar(body.desativacao)
                    // Fallback — desativação sem o bloco novo (coletor já desativado antes
                    // dessa mudança, ou algum caso legado). Ver DesativacaoHandler pro caminho
                    // normal, com drenagem, que é o que o backend usa desde essa entrega.
                    !body.flAtivo -> {
                        Log.w(TAG, "Coletor desativado remotamente (sem bloco de desativação) — desvinculando na hora.")
                        container.deviceRevocationHandler.revoke(
                            "Este tablet foi desativado remotamente pelo suporte. Insira um novo código de ativação."
                        )
                    }
                    else -> {
                        // Carga completa, incondicional — fecha a lacuna do heartbeat rodar com o
                        // app fechado sem nunca puxar admissão/desligamento/reset facial novo, já
                        // que o polling de 30s da tela só roda com o app aberto.
                        container.colaboradorSyncRepository.sincronizarTudo()
                    }
                }
                Result.success()
            } else {
                Log.w(TAG, "Heartbeat recusado: HTTP ${response.code()}")
                Result.retry()
            }
        } catch (e: IOException) {
            Log.w(TAG, "Heartbeat sem conexão, tentando de novo mais tarde: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Falha inesperada no heartbeat: ${e.message}", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "HeartbeatWorker"
    }
}
