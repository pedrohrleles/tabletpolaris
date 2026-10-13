package com.polarisrh.tabletpolaris.data.repository

import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.AgendaPontoResponse
import kotlinx.serialization.json.Json
import java.time.Instant

object TipoMarcacao {
    const val ENTRADA = "1"
    const val SAIDA = "2"
}

/**
 * Agenda de ponto automático (ponto por exceção, Portaria 671). Guardada localmente pra funcionar
 * offline; sem agenda válida o tablet nunca pergunta o tipo da batida e segue o fluxo normal.
 */
class AgendaPontoRepository(
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore
) {

    private val json = Json { ignoreUnknownKeys = true }

    /** Baixa a agenda só se a última cópia tiver mais de 6h — exceto com [forcar] (usado logo
     *  após cadastrar uma facial nova: um colaborador recém-criado pode não estar em nenhuma
     *  cópia anterior, e ele pode bater o ponto minutos depois de cadastrar). Falha é
     *  silenciosa: a cópia antiga continua valendo até `valido_ate` e a próxima tentativa
     *  acontece no próximo gatilho (heartbeat, lote sincronizado ou sync de colaboradores). */
    suspend fun sincronizarSeNecessario(forcar: Boolean = false) {
        val credentials = credentialsStore.read() ?: return
        val agoraMillis = System.currentTimeMillis()
        if (!forcar && agoraMillis - credentialsStore.agendaPontoBaixadaEmMillis() < INTERVALO_SINCRONIZACAO_MS) return

        val response = api.consultarAgendaPontoExcecao(bearerToken = "Bearer ${credentials.token}")
        val body = response.body() ?: return
        if (!response.isSuccessful) return
        credentialsStore.salvarAgendaPonto(
            json = json.encodeToString(AgendaPontoResponse.serializer(), body),
            baixadaEmMillis = agoraMillis
        )
    }

    /** True quando [agora] cai numa jornada de ponto automático dessa matrícula. Sem agenda
     *  válida, ou fora de qualquer janela, a batida segue sem perguntar o tipo. */
    fun estaEmJanelaAutomatica(matricula: String, agora: Instant): Boolean {
        val agenda = lerAgenda() ?: return false
        return runCatching {
            !agora.isAfter(Instant.parse(agenda.validoAte)) &&
                agenda.colaboradores
                    .firstOrNull { it.nrMatricula == matricula }
                    ?.janelas
                    .orEmpty()
                    .any { janela ->
                        !agora.isBefore(Instant.parse(janela.inicio)) && !agora.isAfter(Instant.parse(janela.fim))
                    }
        }.getOrDefault(false)
    }

    private fun lerAgenda(): AgendaPontoResponse? =
        credentialsStore.agendaPontoJson()?.let { runCatching { json.decodeFromString<AgendaPontoResponse>(it) }.getOrNull() }

    private companion object {
        const val INTERVALO_SINCRONIZACAO_MS = 6L * 60 * 60 * 1000
    }
}
