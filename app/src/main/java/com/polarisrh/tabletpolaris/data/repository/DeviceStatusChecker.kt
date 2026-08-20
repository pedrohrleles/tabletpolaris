package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import java.io.IOException

sealed interface DeviceStatusResult {
    data object Active : DeviceStatusResult
    data object Revoked : DeviceStatusResult
    /** Sem conexão, erro 5xx, ou dispositivo ainda não ativado — nunca desvincula sozinho. */
    data object Unknown : DeviceStatusResult
}

/**
 * Consulta GET /coletores/{id}/status. Complementa o heartbeat de 15min: chamado com bem
 * mais frequência (a cada 30s em tela + ao recuperar rede + antes de cada batida), então é
 * o que realmente torna a desativação remota quase instantânea em vez de esperar o heartbeat.
 */
class DeviceStatusChecker(
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore,
    private val onRevoked: (String) -> Unit
) {
    suspend fun checkNow(): DeviceStatusResult {
        val credentials = credentialsStore.read() ?: return DeviceStatusResult.Unknown

        return try {
            val response = api.consultarStatus(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}"
            )
            when {
                response.isSuccessful -> DeviceStatusResult.Active
                response.code() == 401 -> {
                    Log.w(TAG, "Coletor desativado remotamente — limpando credenciais locais.")
                    credentialsStore.clear()
                    onRevoked("Este tablet foi desativado remotamente pelo suporte. Insira um novo código de ativação.")
                    DeviceStatusResult.Revoked
                }
                else -> {
                    Log.w(TAG, "Status recusado: HTTP ${response.code()}")
                    DeviceStatusResult.Unknown
                }
            }
        } catch (e: IOException) {
            Log.w(TAG, "Status sem conexão, tentando de novo mais tarde: ${e.message}")
            DeviceStatusResult.Unknown
        }
    }

    private companion object {
        const val TAG = "DeviceStatusChecker"
    }
}
