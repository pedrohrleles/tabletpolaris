package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Agenda de ponto automático (ponto por exceção) — ver GET rep-p/dispositivos/ponto-excecao/agenda.
 *  Datas em ISO 8601 UTC. [valido_ate] é o limite até onde a agenda vale; depois disso o tablet
 *  não pergunta mais o tipo da batida (o servidor cai na sequência normal). */
@Serializable
data class AgendaPontoResponse(
    @SerialName("gerado_em") val geradoEm: String,
    @SerialName("valido_ate") val validoAte: String,
    val colaboradores: List<AgendaColaboradorDto> = emptyList()
)

@Serializable
data class AgendaColaboradorDto(
    @SerialName("nr_matricula") val nrMatricula: String,
    val janelas: List<JanelaAgendaDto> = emptyList()
)

/** Uma batida feita entre [inicio] e [fim] pertence a uma jornada automática — o servidor já
 *  aplicou regras de escala, feriado e folga. [previstos] só serve pra sugerir o tipo. */
@Serializable
data class JanelaAgendaDto(
    @SerialName("dt_referencia") val dtReferencia: String,
    val inicio: String,
    val fim: String,
    val previstos: List<PrevistoAgendaDto> = emptyList()
)

@Serializable
data class PrevistoAgendaDto(
    @SerialName("tp_marcacao") val tpMarcacao: String,
    @SerialName("dt_hr") val dtHr: String
)
