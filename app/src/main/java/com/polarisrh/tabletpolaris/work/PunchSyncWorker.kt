package com.polarisrh.tabletpolaris.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polarisrh.tabletpolaris.PolarisApplication
import java.io.IOException

/** Drena a fila offline de batidas (`batidas_sincronizadas`) contra o backend — disparado tanto
 *  na hora (logo após cada batida, ver [com.polarisrh.tabletpolaris.data.repository.RoomPunchRepository])
 *  quanto periodicamente como rede de segurança (ver [PolarisApplication]). Nunca roda no
 *  caminho da tela de "Bater Ponto" — a batida já foi salva local e a UI já seguiu em frente
 *  antes desse worker sequer começar. */
class PunchSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PolarisApplication).container
        return try {
            container.punchSyncRepository.sincronizarPendentes()
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync de batidas sem conexão, tentando de novo mais tarde: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Falha inesperada no sync de batidas: ${e.message}", e)
            Result.failure()
        }
    }

    companion object {
        const val PERIODIC_WORK_NAME = "punch_sync_periodic"
        const val IMMEDIATE_WORK_NAME = "punch_sync_immediate"
        private const val TAG = "PunchSyncWorker"
    }
}
