package com.polarisrh.tabletpolaris.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.polarisrh.tabletpolaris.PolarisApplication
import java.io.IOException

/** Disparado uma vez logo após a ativação — puxa o roster de colaboradores pro banco local. */
class ColaboradorSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as PolarisApplication).container
        return try {
            container.colaboradorSyncRepository.sincronizarTudo()
            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync de colaboradores sem conexão, tentando de novo mais tarde: ${e.message}")
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Falha inesperada no sync de colaboradores: ${e.message}", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "ColaboradorSyncWorker"
    }
}
