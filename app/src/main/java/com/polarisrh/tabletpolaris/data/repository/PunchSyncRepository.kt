package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.local.db.BatidaEntity
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacaoDto
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncErrorResponse
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncRequest
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncResponse
import com.polarisrh.tabletpolaris.data.remote.dto.ValidacaoFacialDto
import kotlinx.serialization.json.Json
import retrofit2.Response
import java.time.Instant

/**
 * Drena a fila offline (`batidas_sincronizadas` com status PENDENTE) contra POST
 * rep-p/dispositivos/marcacoes, em lotes. Chamado pelo [com.polarisrh.tabletpolaris.work.PunchSyncWorker]
 * — nunca pela UI diretamente, então nada aqui pode travar a tela de ponto.
 */
class PunchSyncRepository(
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore,
    private val batidaDao: BatidaDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun sincronizarPendentes() {
        val credentials = credentialsStore.read() ?: return
        val pendentes = batidaDao.listarPendentes()
        if (pendentes.isEmpty()) return

        var proximaSequencia = proximaSequenciaValida()

        for (lote in pendentes.chunked(TAMANHO_MAXIMO_LOTE)) {
            var tentativasRealinhamento = 0

            while (true) {
                // Persiste ANTES de enviar — garante que o próximo valor escolhido (mesmo após
                // um crash no meio do envio) seja sempre maior que o último usado, mesmo se o
                // relógio do tablet regredir nesse meio-tempo.
                credentialsStore.salvarUltimaSequenciaLote(proximaSequencia)

                val request = MarcacoesSyncRequest(
                    nrSequenciaLote = proximaSequencia,
                    marcacoes = lote.map { it.paraMarcacaoDto() }
                )
                val response = api.enviarMarcacoes("Bearer ${credentials.token}", request)

                if (response.isSuccessful) {
                    aplicarResultado(lote, response.body())
                    proximaSequencia += 1
                    break
                }

                if (response.code() == 409 && tentativasRealinhamento < MAX_TENTATIVAS_REALINHAMENTO) {
                    tentativasRealinhamento++
                    val ultimaAceita = parseErro409(response)?.nrUltimaSequenciaAceita
                    proximaSequencia = if (ultimaAceita != null) {
                        credentialsStore.salvarUltimaSequenciaLote(ultimaAceita)
                        ultimaAceita + 1
                    } else {
                        proximaSequencia + 1
                    }
                    continue
                }

                // Falha desse lote nessa execução (5xx, rede, ou 409 que não realinhou depois
                // de tentar) — marca e para; o próximo ciclo do worker relê tudo do zero.
                val agora = Instant.now().toString()
                val motivo = if (response.code() == 409) "Sequência de lote desalinhada (HTTP 409)" else "HTTP ${response.code()}"
                Log.w(TAG, "Lote recusado: HTTP ${response.code()}")
                lote.forEach { batidaDao.registrarFalhaSincronizacao(it.id, agora, motivo) }
                return
            }
        }
    }

    /** Nunca gera um timestamp "cru" sem checar o último valor persistido — um relógio que
     *  regrediu (correção NTP, troca de fuso, RTC descarregado) não pode travar a sincronização
     *  indefinidamente. */
    private fun proximaSequenciaValida(): Long {
        val ultimaUsada = credentialsStore.ultimaSequenciaLote() ?: 0L
        return maxOf(System.currentTimeMillis(), ultimaUsada + 1)
    }

    /** O 201 não significa que todo o lote foi aceito — cada marcação é processada
     *  isoladamente. Aceitas e duplicadas saem da fila; rejeitadas também saem (motivo é
     *  definitivo), mas ficam registradas com o motivo pra consulta posterior. Marcações
     *  enviadas mas ausentes dos três grupos (corpo omisso/inesperado) ficam pendentes — nunca
     *  assume sucesso sem confirmação explícita. */
    private suspend fun aplicarResultado(lote: List<BatidaEntity>, body: MarcacoesSyncResponse?) {
        val agora = Instant.now().toString()
        val porIdLocal = lote.associateBy { it.idLocal }

        body?.aceitas?.forEach { aceita ->
            porIdLocal[aceita.idLocal]?.let { batidaDao.marcarComoSincronizado(it.id, agora) }
        }
        body?.duplicadas?.forEach { duplicada ->
            porIdLocal[duplicada.idLocal]?.let { batidaDao.marcarComoSincronizado(it.id, agora) }
        }
        body?.rejeitadas?.forEach { rejeitada ->
            porIdLocal[rejeitada.idLocal]?.let { batida ->
                val motivo = rejeitada.motivo ?: "Rejeitada pelo servidor"
                Log.w(TAG, "Marcação rejeitada definitivamente — matrícula=${batida.matricula} motivo=$motivo")
                batidaDao.marcarComoRejeitada(batida.id, agora, motivo)
            }
        }
    }

    private fun parseErro409(response: Response<MarcacoesSyncResponse>): MarcacoesSyncErrorResponse? =
        response.errorBody()?.string()?.let { corpo ->
            runCatching { json.decodeFromString<MarcacoesSyncErrorResponse>(corpo) }.getOrNull()
        }

    private fun BatidaEntity.paraMarcacaoDto() = MarcacaoDto(
        idLocal = idLocal,
        nrMatricula = matricula,
        dtHrMarcacao = dtHrMarcacao,
        assinatura = assinatura,
        validacaoFacial = ValidacaoFacialDto(nrScore = nrScore, nrThreshold = nrThreshold)
    )

    private companion object {
        const val TAMANHO_MAXIMO_LOTE = 50
        const val MAX_TENTATIVAS_REALINHAMENTO = 2
        const val TAG = "PunchSyncRepository"
    }
}
