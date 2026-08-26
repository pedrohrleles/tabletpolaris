package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * nr_drift_segundos e dt_ultimo_heartbeat propositalmente NÃO estão aqui — são calculados
 * pelo servidor a partir de dt_dispositivo e do momento em que ele recebe a requisição,
 * não algo que o tablet deveria computar e enviar por conta própria.
 */
@Serializable
data class HeartbeatRequest(
    @SerialName("nr_fila_pendente") val nrFilaPendente: Int,
    @SerialName("nr_bateria_pct") val nrBateriaPct: Int,
    @SerialName("fl_carregando") val flCarregando: Boolean,
    @SerialName("armazenamento") val armazenamento: String,
    @SerialName("mem_ram") val memRam: String,
    @SerialName("ds_versao_app") val dsVersaoApp: String,
    @SerialName("ds_android_id") val dsAndroidId: String,
    @SerialName("nr_serie_dispositivo") val nrSerieDispositivo: String,
    @SerialName("fl_strongbox") val flStrongbox: Boolean,
    @SerialName("dt_dispositivo") val dtDispositivo: String
)

/**
 * fl_ativo aqui é o admin dizendo, no painel, se esse coletor continua autorizado a
 * funcionar — se vier false, o app limpa as credenciais locais e volta pra ativação.
 *
 * Default = true: se o backend não mandar esse campo (ainda), o app assume que continua
 * ativo em vez de travar o tablet por engano ou quebrar ao tentar ler a resposta.
 *
 * dt_cadastro_alterado é o mesmo campo que já existe em StatusResponse — mesma semântica,
 * mesmo gatilho de sincronização incremental de colaboradores (ver
 * ColaboradorSyncRepository.sincronizarSeNecessario). Repetido aqui pra fechar a lacuna de o
 * heartbeat rodar em segundo plano (app fechado) sem nunca puxar admissão/desligamento nova,
 * já que o /status só é consultado com a tela de ponto aberta.
 */
@Serializable
data class HeartbeatResponse(
    @SerialName("fl_ativo") val flAtivo: Boolean = true,
    @SerialName("dt_cadastro_alterado") val dtCadastroAlterado: String? = null
)
