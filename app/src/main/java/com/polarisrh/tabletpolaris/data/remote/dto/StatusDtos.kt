package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StatusResponse(
    val id: String,
    @SerialName("nm_dispositivo") val nomeDispositivo: String,
    @SerialName("fl_ativo") val flAtivo: Boolean,
    @SerialName("fl_vinculado") val flVinculado: Boolean,
    @SerialName("dt_servidor") val dtServidor: String
)
