package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AtivarTabletRequest(
    val codigo: String,
    @SerialName("cd_chave_publica") val cdChavePublica: String,
    @SerialName("nr_serie_dispositivo") val nrSerieDispositivo: String,
    @SerialName("ds_android_id") val dsAndroidId: String,
    @SerialName("ds_versao_app") val dsVersaoApp: String,
    @SerialName("fl_strongbox") val flStrongbox: Boolean,
    @SerialName("dt_dispositivo") val dtDispositivo: String,
    @SerialName("nr_bateria_pct") val nrBateriaPct: Int,
    @SerialName("fl_carregando") val flCarregando: Boolean,
    @SerialName("nr_fila_pendente") val nrFilaPendente: Int
)

@Serializable
data class DispositivoDto(
    val id: String,
    @SerialName("nm_dispositivo") val nomeDispositivo: String
)

@Serializable
data class EmpresaDto(
    val id: String,
    val nome: String
)

@Serializable
data class LocalDto(
    val tipo: String,
    val id: String,
    val nome: String,
    val timezone: String
)

@Serializable
data class AtivarTabletResponse(
    val token: String,
    @SerialName("id_coletor") val idColetor: String,
    @SerialName("id_coletor_logico") val idColetorLogico: String,
    @SerialName("id_empregador") val idEmpregador: String,
    @SerialName("id_estabelecimento") val idEstabelecimento: String? = null,
    @SerialName("nome_empregador") val nomeEmpregador: String,
    val dispositivo: DispositivoDto,
    val empresa: EmpresaDto,
    val local: LocalDto,
    @SerialName("dt_servidor") val dtServidor: String,
    @SerialName("nr_drift_segundos") val nrDriftSegundos: Int,
    @SerialName("fl_exige_ajuste_relogio") val flExigeAjusteRelogio: Boolean
)

@Serializable
data class ErroResponse(
    @SerialName("erro") val erro: String,
    @SerialName("message") val message: String? = null
)
