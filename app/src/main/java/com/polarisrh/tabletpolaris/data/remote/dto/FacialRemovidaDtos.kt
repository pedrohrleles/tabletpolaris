package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemocaoFacialDto(
    @SerialName("nr_matricula") val nrMatricula: String,
    @SerialName("dt_remocao") val dtRemocao: String
)

@Serializable
data class FacialRemovidaRequest(
    val remocoes: List<RemocaoFacialDto>
)

/** Mesmo formato de item que [FacialRemovidaRequest] — o backend aceita o array com o nome
 *  "cadastros" ou "remocoes" nesta rota, mas usamos "cadastros" aqui por clareza no código.
 *  dt_remocao no item, apesar do nome (reuso da estrutura da outra rota, confirmado com o
 *  backend), aqui significa "quando o cadastro foi feito". */
@Serializable
data class FacialCadastradaRequest(
    val cadastros: List<RemocaoFacialDto>
)

@Serializable
data class ConfirmacaoFacialDto(
    @SerialName("nr_matricula") val nrMatricula: String
)

@Serializable
data class RejeicaoFacialDto(
    @SerialName("nr_matricula") val nrMatricula: String,
    val motivo: String? = null
)

/** Resposta idêntica nas duas rotas (facial-cadastrada e facial-removida) — cada matrícula é
 *  processada isoladamente, então uma rejeição não derruba as outras do mesmo lote. */
@Serializable
data class FacialNotificacaoResponse(
    val confirmadas: List<ConfirmacaoFacialDto> = emptyList(),
    val rejeitadas: List<RejeicaoFacialDto> = emptyList()
)
