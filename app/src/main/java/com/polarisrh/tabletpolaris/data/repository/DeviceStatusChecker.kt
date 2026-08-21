package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.StatusResponse
import java.io.IOException
import java.time.Instant

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
    private val colaboradorSyncRepository: ColaboradorSyncRepository,
    private val revocationHandler: DeviceRevocationHandler
) {
    suspend fun checkNow(): DeviceStatusResult {
        val credentials = credentialsStore.read() ?: return DeviceStatusResult.Unknown

        return try {
            val response = api.consultarStatus(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}"
            )
            when {
                response.isSuccessful -> {
                    sincronizarColaboradoresSeNecessario(response.body())
                    DeviceStatusResult.Active
                }
                response.code() == 401 -> {
                    Log.w(TAG, "Coletor desativado remotamente — desvinculando.")
                    revocationHandler.revoke("Este tablet foi desativado remotamente pelo suporte. Insira um novo código de ativação.")
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

    /** dt_cadastro_alterado mais recente que a última sincronização aplicada = alguém foi
     *  admitido/desligado desde então — busca só o delta em vez de esperar outro gatilho. */
    private suspend fun sincronizarColaboradoresSeNecessario(body: StatusResponse?) {
        val dtCadastroAlterado = body?.dtCadastroAlterado ?: return
        val ultimaSincronizacao = credentialsStore.ultimaSincronizacaoColaboradores()

        val precisaSincronizar = ultimaSincronizacao == null ||
            Instant.parse(dtCadastroAlterado) > Instant.parse(ultimaSincronizacao)

        if (precisaSincronizar) {
            colaboradorSyncRepository.sincronizarDelta()
        }
    }

    private companion object {
        const val TAG = "DeviceStatusChecker"
    }
}
