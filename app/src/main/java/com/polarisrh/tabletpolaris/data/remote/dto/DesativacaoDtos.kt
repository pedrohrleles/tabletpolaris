package com.polarisrh.tabletpolaris.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Bloco presente (não nulo) em GET /status, POST /heartbeat e POST /marcacoes sempre que este
 * coletor tiver uma desativação em andamento — null quando ativo normalmente.
 *
 * [flBloquearNovasMarcacoes] é quem manda de verdade na tela de Bater Ponto (bloqueia teclado +
 * Confirmar) — [DesativacaoHandler] trata a simples PRESENÇA deste bloco com fl_desativado como
 * o gatilho de bloqueio local, capturado no instante em que a resposta chega (ver nota do
 * backend: o servidor não segura nada, não tem como saber quando o tablet recebeu o aviso — o
 * bloqueio local é a proteção principal, não uma rede de segurança).
 */
@Serializable
data class DesativacaoDto(
    @SerialName("fl_desativado") val flDesativado: Boolean,
    @SerialName("fl_bloquear_novas_marcacoes") val flBloquearNovasMarcacoes: Boolean = true,
    @SerialName("fl_sincronizacao_liberada") val flSincronizacaoLiberada: Boolean = true,
    @SerialName("tp_origem") val tpOrigem: String? = null,
    @SerialName("dt_solicitada") val dtSolicitada: String? = null,
    @SerialName("nr_fila_pendente_conhecida") val nrFilaPendenteConhecida: Int? = null,
    @SerialName("acao_requerida") val acaoRequerida: String? = null,
    @SerialName("ds_mensagem") val dsMensagem: String? = null
)

/** GET /coletores/{id}/desativacao — consultado uma vez no startup do app, antes de abrir a
 *  tela de ponto, pra cobrir o caso de o tablet ter sido desligado/reiniciado sem nunca ter
 *  recebido o aviso pelos outros três canais (que só rodam com o app já em execução). */
@Serializable
data class ConsultarDesativacaoResponse(
    val id: String? = null,
    @SerialName("nm_dispositivo") val nomeDispositivo: String? = null,
    val estado: String? = null,
    val desativacao: DesativacaoDto? = null,
    @SerialName("dt_servidor") val dtServidor: String? = null
)

/** POST /coletores/{id}/desativacao/confirmar — corpo todo opcional, {} já vale como "terminei"
 *  (confirmado com o backend). Só mandamos quando a fila local já está mesmo vazia. */
@Serializable
data class ConfirmarDesativacaoRequest(
    @SerialName("nr_fila_pendente") val nrFilaPendente: Int? = null,
    @SerialName("nr_marcacoes_sincronizadas") val nrMarcacoesSincronizadas: Int? = null,
    @SerialName("dt_dispositivo") val dtDispositivo: String? = null
)

/** Se [flEncerrado] vier true, o ciclo fechou de vez — daí em diante tudo 401, e é o gatilho
 *  pra limpar as credenciais locais. Reenvio é idempotente ([flEncerradoNestaChamada] diz se
 *  foi ESSA chamada que fechou ou se já estava fechado antes). Se a gente declarar fila > 0
 *  (não deveria acontecer, já que só chamamos com fila confirmada vazia), o servidor não fecha
 *  a janela — "acredita no aparelho". */
@Serializable
data class ConfirmarDesativacaoResponse(
    @SerialName("fl_encerrado") val flEncerrado: Boolean = false,
    @SerialName("fl_sincronizacao_liberada") val flSincronizacaoLiberada: Boolean = true,
    @SerialName("fl_encerrado_nesta_chamada") val flEncerradoNestaChamada: Boolean = false
)
