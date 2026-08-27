package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val id: String,
    @SerialName("nm_dispositivo") val nomeDispositivo: String,
    @SerialName("fl_ativo") val flAtivo: Boolean,
    @SerialName("fl_vinculado") val flVinculado: Boolean,
    @SerialName("dt_servidor") val dtServidor: String,
    // Se mais recente que a última sincronização de colaboradores, dispara um sync incremental.
    @SerialName("dt_cadastro_alterado") val dtCadastroAlterado: String? = null,
    // Não nulo = desativação em andamento (ver DesativacaoHandler) — substitui a desativação
    // imediata via fl_ativo=false pra esse caso específico (fl_ativo continua respondendo só
    // "posso bater ponto agora?", sem carregar mais o "porquê").
    val desativacao: DesativacaoDto? = null
)
