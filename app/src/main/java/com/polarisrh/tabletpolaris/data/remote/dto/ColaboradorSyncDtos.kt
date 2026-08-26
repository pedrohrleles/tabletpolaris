package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ColaboradorDto(
    @SerialName("nr_matricula") val matricula: String,
    @SerialName("nr_cpf") val cpf: String,
    @SerialName("nm_colaborador") val nome: String,
    @SerialName("fl_ativo") val ativo: Boolean,
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
