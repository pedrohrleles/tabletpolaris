package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.local.db.BatidaEntity
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacaoDto
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncErro400
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

        // NUNCA pode processar um lote mais recente antes de um mais antigo que ainda não
        // sincronizou: listarPendentes() ordena por dt_hr_marcacao ASC, e o NSR (número
        // sequencial de registro, exigência legal da Portaria 671) tem que refletir a ordem
        // REAL das batidas — se o lote das 8:35 do João fosse aceito antes do lote das 8:30 da
        // Maria (que ficou preso numa falha), o NSR sairia fora de ordem. Por isso qualquer
        // falha de lote para a execução inteira aqui, em vez de pular pro próximo.
        for (lote in pendentes.chunked(TAMANHO_MAXIMO_LOTE)) {
            var tentativasRealinhamento = 0

            while (true) {
                // Persiste ANTES de enviar — garante que o próximo valor escolhido (mesmo após
                // um crash no meio do envio) seja sempre maior que o último usado, mesmo se o
                // relógio do tablet regredir nesse meio-tempo.
                credentialsStore.salvarUltimaSequenciaLote(proximaSequencia)

                val request = MarcacoesSyncRequest(
                    nrSequenciaLote = proximaSequencia,
                    // Capturado agora, na hora do POST — não quando a fila foi montada.
                    dtDispositivo = Instant.now().toString(),
                    marcacoes = lote.map { it.paraMarcacaoDto() }
                )
                val response = api.enviarMarcacoes("Bearer ${credentials.token}", request)

                if (response.isSuccessful) {
                    aplicarResultado(lote, response.body())
                    proximaSequencia += 1
                    break
                }

                if (response.code() == 400) {
                    val erro400 = parseErro400(response)
                    if (erro400?.erro == "relogio_dessincronizado") {
                        // Falha temporária — mantém a fila (não marca como sincronizado nem
                        // rejeitado) e NÃO avança a sequência: o próximo ciclo relê tudo do
                        // zero e tenta de novo sozinho assim que o relógio for corrigido.
                        Log.w(
                            TAG,
                            "Relógio do tablet dessincronizado — drift=${erro400.nrDriftSegundos}s " +
                                "(máximo corrigível: ${erro400.nrDriftMaximoCorrigivelSegundos}s). " +
                                "Fila mantida, sequência não avança até o relógio ser corrigido."
                        )
                        val agora = Instant.now().toString()
                        val motivo = erro400.message ?: "Relógio do tablet dessincronizado"
                        lote.forEach { batidaDao.registrarFalhaSincronizacao(it.id, agora, motivo) }
                        return
                    }
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
            porIdLocal[aceita.idLocal]?.let { batida ->
                batidaDao.marcarComoSincronizado(batida.id, agora)
                // A hora gravada pelo backend é a corrigida — pode diferir da que mandamos.
                // Essa diferença é o desvio real do relógio do tablet no momento da batida.
                val corrigida = aceita.dtHrMarcacaoCorrigida
                if (corrigida != null && corrigida != batida.dtHrMarcacao) {
                    Log.i(
                        TAG,
                        "Horário corrigido pelo servidor — matrícula=${batida.matricula} " +
                            "enviado=${batida.dtHrMarcacao} gravado=$corrigida"
                    )
                }
            }
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

    private fun parseErro400(response: Response<MarcacoesSyncResponse>): MarcacoesSyncErro400? =
        response.errorBody()?.string()?.let { corpo ->
            runCatching { json.decodeFromString<MarcacoesSyncErro400>(corpo) }.getOrNull()
        }

    private fun BatidaEntity.paraMarcacaoDto() = MarcacaoDto(
        idLocal = idLocal,
        nrMatricula = matricula,
        dtHrMarcacao = dtHrMarcacao,
        assinatura = assinatura,
        validacaoFacial = ValidacaoFacialDto(nrScore = nrScore, nrThreshold = nrThreshold)
    )

    private companion object {
        // Reduzido de 50 pra 15 depois de ver, em produção, um lote de ~7 marcações já levar
        // pouco mais de 4s no backend pra processar (assinatura + gravação + auditoria de cada
        // uma) — um lote de 50 chegava perto do teto de 30s do syncService, sem margem pra rede
        // mais lenta ou o backend ocupado. Com 15, mesmo um dia ruim fica bem abaixo do timeout.
        const val TAMANHO_MAXIMO_LOTE = 15
        const val MAX_TENTATIVAS_REALINHAMENTO = 2
        const val TAG = "PunchSyncRepository"
    }
}
