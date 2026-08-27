package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Auditoria pura — o backend não valida nem compara, só grava (coletor-marcacoes.service.ts:612-620
 *  confirmou que só checa se nr_threshold é um número finito). */
@Serializable
data class ValidacaoFacialDto(
    @SerialName("nr_score") val nrScore: Float,
    @SerialName("nr_threshold") val nrThreshold: Float
)

@Serializable
data class MarcacaoDto(
    @SerialName("id_local") val idLocal: String,
    @SerialName("nr_matricula") val nrMatricula: String,
    @SerialName("dt_hr_marcacao") val dtHrMarcacao: String,
    val assinatura: String,
    @SerialName("validacao_facial") val validacaoFacial: ValidacaoFacialDto
)

/** [nrSequenciaLote] é por lote (requisição), não por marcação — estritamente maior que a
 *  última aceita para esse coletor (saltos são permitidos). Gerado a partir de
 *  System.currentTimeMillis() combinado com o último valor persistido (ver
 *  DeviceCredentialsStore.ultimaSequenciaLote) — protege contra o relógio do tablet regredir.
 *  [dtDispositivo] é o relógio do tablet no instante do POST (não de quando a fila foi
 *  montada) — mesmo valor que já mandamos no heartbeat; o backend usa pra detectar relógio
 *  dessincronizado (ver MarcacoesSyncErro400). */
@Serializable
data class MarcacoesSyncRequest(
    @SerialName("nr_sequencia_lote") val nrSequenciaLote: Long,
    @SerialName("dt_dispositivo") val dtDispositivo: String,
    val marcacoes: List<MarcacaoDto>
)

/** O 201 não significa que todas as marcações do lote foram aceitas — cada uma é processada
 *  isoladamente. [aceitas] e [duplicadas] saem da fila; [rejeitadas] também saem (motivo é
 *  definitivo, reenviar não muda o resultado), mas o motivo fica registrado localmente. */
@Serializable
data class MarcacoesSyncResponse(
    @SerialName("nr_sequencia_lote") val nrSequenciaLote: Long? = null,
    val aceitas: List<MarcacaoAceitaDto> = emptyList(),
    val duplicadas: List<MarcacaoIdLocalDto> = emptyList(),
    val rejeitadas: List<MarcacaoRejeitadaDto> = emptyList()
)

@Serializable
data class MarcacaoAceitaDto(
    @SerialName("id_local") val idLocal: String,
    // Instante já ancorado no relógio do servidor (ISO 8601 UTC) — pode diferir do
    // dt_hr_marcacao que mandamos; a diferença é o desvio real do relógio deste tablet (ver
    // log em PunchSyncRepository.aplicarResultado). Confirmado com o backend — mesmo nome do
    // contrato de envio. id_marcacao/nr_nsr/tp_marcacao/comprovante também vêm na resposta, mas
    // não são usados aqui — nada nesse fluxo depende deles hoje.
    @SerialName("dt_hr_marcacao") val dtHrMarcacaoCorrigida: String? = null
)

@Serializable
data class MarcacaoIdLocalDto(
    @SerialName("id_local") val idLocal: String
)

@Serializable
data class MarcacaoRejeitadaDto(
    @SerialName("id_local") val idLocal: String,
    val motivo: String? = null
)

/** Corpo de erro do HTTP 409 (sequência de lote desalinhada) — usar SEMPRE
 *  nr_ultima_sequencia_aceita + 1 pro próximo envio (nunca gerar outro timestamp na hora,
 *  senão um relógio que regrediu trava a sincronização até o relógio "alcançar" o valor
 *  antigo). */
@Serializable
data class MarcacoesSyncErrorResponse(
    @SerialName("nr_ultima_sequencia_aceita") val nrUltimaSequenciaAceita: Long? = null
)

/** Corpo de erro do HTTP 400 quando o relógio do tablet está fora de sincronia com a Hora
 *  Legal Brasileira. Falha temporária — mantém a fila local, não avança nr_sequencia_lote;
 *  depois do relógio corrigido, o próximo ciclo tenta de novo sozinho. */
@Serializable
data class MarcacoesSyncErro400(
    val erro: String? = null,
    val message: String? = null,
    @SerialName("nr_drift_segundos") val nrDriftSegundos: Long? = null,
    @SerialName("nr_drift_maximo_corrigivel_segundos") val nrDriftMaximoCorrigivelSegundos: Long? = null
)
