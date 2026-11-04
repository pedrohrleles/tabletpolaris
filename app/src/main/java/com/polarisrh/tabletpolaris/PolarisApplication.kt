package com.polarisrh.tabletpolaris

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
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
        garantirModeloFacialDisponivel()
    }

    /**
     * Desde a troca do detector de rosto pra variante "não-empacotada" do ML Kit (ver
     * libs.versions.toml), o modelo não vem mais dentro do .apk — é baixado pelo Google Play
     * Services. A tag no AndroidManifest já pede pra Play Store baixar isso na instalação, mas
     * isso não é documentado como cobrindo ATUALIZAÇÃO de um app já instalado (só instalação
     * nova) — e é exatamente o caso dos tablets que já estão em campo. Rodando essa checagem
     * aqui, toda vez que o app abre (instalação nova ou não), garantimos o download também
     * nesse caminho, sem esperar o funcionário abrir a câmera pra bater ponto.
     */
    private fun garantirModeloFacialDisponivel() {
        val detector = FaceDetection.getClient(FaceDetectorOptions.Builder().build())
        val moduleInstallClient = ModuleInstall.getClient(this)

        moduleInstallClient.areModulesAvailable(detector)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    detector.close()
                    return@addOnSuccessListener
                }
                val request = ModuleInstallRequest.newBuilder()
                    .addApi(detector)
                    .build()
                moduleInstallClient.installModules(request)
                    // Fecha o detector só DEPOIS do installModules() terminar (sucesso ou
                    // falha) — fechar antes derruba a conexão que o Play Services usa pra
                    // devolver esse resultado, e o callback nunca chega (confirmado em teste:
                    // GMS registra o módulo como "pendente" e trava aí).
                    .addOnSuccessListener { detector.close() }
                    .addOnFailureListener { detector.close() }
            }
            .addOnFailureListener { detector.close() }
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
