package com.polarisrh.tabletpolaris.data.repository

import android.content.Context
import android.util.Log
import com.polarisrh.tabletpolaris.BuildConfig
import com.polarisrh.tabletpolaris.data.local.DeviceCredentials
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.DeviceIdentity
import com.polarisrh.tabletpolaris.data.local.DeviceKeyManager
import com.polarisrh.tabletpolaris.data.local.DeviceTelemetryCollector
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.AtivarTabletRequest
import com.polarisrh.tabletpolaris.data.remote.dto.ErroResponse
import com.polarisrh.tabletpolaris.work.ColaboradorSyncWorker
import com.polarisrh.tabletpolaris.work.HeartbeatWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant

/**
 * Real implementation, backed by the Polaris RH activation endpoint. Not wired into
 * [com.polarisrh.tabletpolaris.AppContainer] as a stub anymore — this is the active
 * implementation once a live backend URL is configured.
 */
class RemoteDeviceAuthRepository(
    private val context: Context,
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore,
    private val colaboradorSyncRepository: ColaboradorSyncRepository,
    private val batidaDao: BatidaDao,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager(),
    private val telemetryCollector: DeviceTelemetryCollector = DeviceTelemetryCollector(context)
) : DeviceAuthRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun activateDevice(activationCode: String): Result<Unit> {
        if (activationCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o código de ativação"))
        }

        return try {
            val telemetry = telemetryCollector.collect()
            val request = AtivarTabletRequest(
                codigo = activationCode,
                cdChavePublica = deviceKeyManager.getOrCreatePublicKeyBase64(),
                // Android bloqueia o número de série real pra apps comuns desde o Android 10 (mesmo
                // com permissão, a API devolve "UNKNOWN") — não vale pedir READ_PHONE_STATE pra isso.
                nrSerieDispositivo = "UNKNOWN",
                dsAndroidId = DeviceIdentity.androidId(context),
                dsVersaoApp = BuildConfig.VERSION_NAME,
                flStrongbox = deviceKeyManager.isStrongBoxBacked(),
                dtDispositivo = Instant.now().toString(),
                nrBateriaPct = telemetry.batteryPercent,
                flCarregando = telemetry.isCharging,
                nrFilaPendente = batidaDao.contarPendentes(),
                armazenamento = telemetry.armazenamento,
                memRam = telemetry.memoriaRam
            )

            val response = api.ativarTablet(request)
            val body = response.body()

            if (response.isSuccessful && body != null) {
                if (body.flExigeAjusteRelogio) {
                    return Result.failure(
                        IllegalStateException(
                            "O relógio do tablet está desconfigurado. Ajuste a data e hora nas configurações do Android e tente novamente."
                        )
                    )
                }

                // Decide, antes de salvar a sessão nova, se o cache local de colaboradores
                // (embeddings inclusive) deve ser preservado (mesma empresa de antes) ou
                // zerado (empresa diferente) — roster agora é por empresa inteira, não mais
                // por estabelecimento.
                colaboradorSyncRepository.prepararParaAtivacao(body.idEmpregador)

                credentialsStore.save(
                    DeviceCredentials(
                        token = body.token,
                        idColetor = body.idColetor,
                        idColetorLogico = body.idColetorLogico,
                        idEmpregador = body.idEmpregador,
                        idEstabelecimento = body.idEstabelecimento,
                        nomeEmpregador = body.nomeEmpregador,
                        timezone = body.local.timezone
                    )
                )
                // O worker periódico só roda pela 1ª vez depois de 15min do agendamento — sem
                // isso o tablet ficaria reportando dados desatualizados até lá.
                val workManager = WorkManager.getInstance(context)
                workManager.enqueue(OneTimeWorkRequestBuilder<HeartbeatWorker>().build())

                // Espera a carga completa do roster terminar ANTES de liberar a ativação — só
                // agendar o worker e seguir em frente deixava a tela de ponto acessível com o
                // banco local ainda vazio/parcial, então as primeiras tentativas de bater ponto
                // (principalmente de colaboradores de outro local de trabalho, cuja matrícula só
                // existe aqui depois desse sync) "não reconheciam" a matrícula até o worker
                // rodar por conta própria em segundo plano. Falha aqui não derruba a ativação —
                // o worker abaixo serve de retry (inclusive com backoff em caso de IOException).
                try {
                    colaboradorSyncRepository.sincronizarTudo()
                } catch (e: Exception) {
                    Log.w(TAG, "Sync inicial de colaboradores falhou, worker vai tentar de novo: ${e.message}")
                }
                workManager.enqueue(OneTimeWorkRequestBuilder<ColaboradorSyncWorker>().build())
                Result.success(Unit)
            } else {
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "Ativação falhou: HTTP ${response.code()} | corpo: $errorBody")
                val erro = errorBody?.let {
                    runCatching { json.decodeFromString<ErroResponse>(it) }.getOrNull()
                }
                val mensagem = erro?.message ?: erro?.erro ?: "Não foi possível ativar o tablet (HTTP ${response.code()})"
                Result.failure(IllegalStateException(mensagem))
            }
        } catch (e: IOException) {
            Log.e(TAG, "Falha ao ativar tablet: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(IllegalStateException("Sem conexão com o servidor. Verifique a internet e tente novamente."))
        } catch (e: Exception) {
            Log.e(TAG, "Erro inesperado ao ativar tablet: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(IllegalStateException("Erro inesperado: ${e.message}"))
        }
    }

    override fun hasStoredCredentials(): Boolean = credentialsStore.read() != null

    private companion object {
        const val TAG = "RemoteDeviceAuth"
    }
}
