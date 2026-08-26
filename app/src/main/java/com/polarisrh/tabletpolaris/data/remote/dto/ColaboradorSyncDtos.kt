package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ColaboradorDto(
    @SerialName("nr_matricula") val matricula: String,
    // Nulos quando fl_isento = true — o backend não manda CPF/nome pra quem é isento de ponto.
    @SerialName("nr_cpf") val cpf: String? = null,
    @SerialName("nm_colaborador") val nome: String? = null,
    @SerialName("fl_ativo") val ativo: Boolean,
    /** Vínculo ativo, mas dispensado de bater ponto — vem com fl_ativo=false no payload
     *  (aparente reuso do campo pra "recusar batida"), mas tratamos como sempre ativo pra não
     *  confundir com desligamento de verdade (ver ColaboradorSyncRepository). */
    @SerialName("fl_isento") val isento: Boolean = false,
    @SerialName("atualizado_em") val atualizadoEm: String,
    /** Presente quando o colaborador pediu reset da facial pelo painel web. Comparado contra
     *  o último reset já aplicado localmente (ColaboradorEntity.dtResetFacialAplicado) — só
     *  apaga o embedding se for um comando novo, não visto antes. */
    @SerialName("dt_reset_facial") val dtResetFacial: String? = null
)

@Serializable
data class ColaboradoresSyncResponse(
    @SerialName("dt_sincronizacao") val dtSincronizacao: String,
    @SerialName("proximo_cursor") val proximoCursor: String? = null,
    val colaboradores: List<ColaboradorDto>
)
