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
 *  DeviceCredentialsStore.ultimaSequenciaLote) — protege contra o relógio do tablet regredir. */
@Serializable
data class MarcacoesSyncRequest(
    @SerialName("nr_sequencia_lote") val nrSequenciaLote: Long,
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
    @SerialName("id_local") val idLocal: String
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
