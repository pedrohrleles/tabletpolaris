package com.polarisrh.tabletpolaris.data.repository

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.ConfirmarDesativacaoRequest
import com.polarisrh.tabletpolaris.data.remote.dto.DesativacaoDto
import com.polarisrh.tabletpolaris.work.PunchSyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import java.time.Instant

/**
 * Desativação com drenagem: quando o RH desativa o tablet, ele NÃO é desligado na hora — a
 * fila de pontos pendente precisa ser sincronizada primeiro. O servidor não segura nada do
 * lado dele (não tem como saber quando o tablet recebeu o aviso), então o bloqueio local,
 * capturado no INSTANTE em que vemos o bloco `desativacao` pela primeira vez, é a proteção
 * principal — não uma rede de segurança. Qualquer ponto batido depois desse instante é
 * descartado (nem entra na fila, ver [RoomPunchRepository.registerPunch]), mesmo que a pessoa
 * já estivesse no meio do reconhecimento facial.
 *
 * [mensagemBloqueio] é observado pela tela de "Bater Ponto" pra mostrar "Tablet desativado" e
 * desabilitar teclado/Confirmar — não nulo assim que [processar] vê a primeira vez, permanece
 * assim (mesmo depois de reiniciar o app) até a desativação de fato se concluir.
 */
class DesativacaoHandler(
    private val context: Context,
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore,
    private val batidaDao: BatidaDao,
    private val revocationHandler: DeviceRevocationHandler
) {
    private val _mensagemBloqueio = MutableStateFlow<String?>(
        if (credentialsStore.estaBloqueadoPorDesativacao()) MENSAGEM_PADRAO else null
    )
    val mensagemBloqueio: StateFlow<String?> = _mensagemBloqueio

    /** Chamado numa ativação nova bem-sucedida (ver [RemoteDeviceAuthRepository]) — sem isso, o
     *  estado em memória (esse handler é um singleton vivo enquanto o processo do app não
     *  reinicia) continuava marcado como bloqueado depois de reativar com um coletor novo,
     *  mesmo já tendo limpado as credenciais antigas. Limpa tanto o flag persistido quanto o
     *  StateFlow em memória, pros dois nunca ficarem fora de sincronia. */
    fun resetar() {
        credentialsStore.limparBloqueioPorDesativacao()
        _mensagemBloqueio.value = null
    }

    /** Chamar sempre que uma resposta de /status, /heartbeat ou /marcacoes trouxer esse bloco
     *  (não nulo). Idempotente — seguro chamar de novo mesmo já estando bloqueado. */
    suspend fun processar(bloco: DesativacaoDto?) {
        if (bloco == null || !bloco.flDesativado) return

        // O ciclo pode fechar de duas formas (contrato atualizado): pelo ÚLTIMO lote de
        // marcações, se ele já foi enviado com nr_fila_pendente=0 (nosso caso — sempre mandamos
        // o valor real), ou pela confirmação explícita (ver tentarConcluirSeNecessario). Nos
        // dois casos, fl_sincronizacao_liberada vem false quando já fechou — não sobra mais
        // nada a fazer, só concluir.
        if (!bloco.flSincronizacaoLiberada) {
            Log.w(TAG, "Janela de sincronização já fechada pelo servidor — desvinculando.")
            revocationHandler.revoke("Este tablet foi desativado pelo administrador. Insira um novo código de ativação.")
            return
        }

        if (!credentialsStore.estaBloqueadoPorDesativacao()) {
            Log.w(
                TAG,
                "Desativação recebida (origem=${bloco.tpOrigem}, mensagem=${bloco.dsMensagem}) — " +
                    "bloqueando localmente e drenando a fila."
            )
            credentialsStore.marcarBloqueadoPorDesativacao()
        }
        // Texto fixo na tela por pedido explícito — curto, só pra facilitar debug em campo. A
        // mensagem de verdade do backend (ds_mensagem) vai só pro log, acima.
        _mensagemBloqueio.value = MENSAGEM_PADRAO

        dispararSincronizacaoImediata()
    }

    /** Chamado pelo [PunchSyncWorker] depois de toda tentativa de drenagem — se estivermos
     *  bloqueados e a fila JÁ estiver confirmadamente vazia, fecha o ciclo com o servidor. Sem
     *  efeito nenhum se não estivermos numa desativação em andamento. */
    suspend fun tentarConcluirSeNecessario() {
        if (!credentialsStore.estaBloqueadoPorDesativacao()) return
        val credentials = credentialsStore.read() ?: return
        if (batidaDao.contarPendentes() > 0) return // ainda tem o que sincronizar

        val request = ConfirmarDesativacaoRequest(
            nrFilaPendente = 0,
            dtDispositivo = Instant.now().toString()
        )

        val response = try {
            api.confirmarDesativacao(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}",
                request = request
            )
        } catch (e: IOException) {
            Log.w(TAG, "Confirmação de desativação sem conexão, tentando de novo mais tarde: ${e.message}")
            return
        }

        val body = response.body()
        when {
            response.isSuccessful && body?.flEncerrado == true -> {
                Log.w(TAG, "Desativação confirmada pelo servidor — desvinculando.")
                revocationHandler.revoke("Este tablet foi desativado pelo administrador. Insira um novo código de ativação.")
            }
            // Conclusão, não erro (contrato atualizado): se o último lote de marcações já
            // fechou a janela sozinho (nr_fila_pendente=0 nele), essa confirmação chega DEPOIS
            // do fechamento e o servidor responde 401 — não é um problema de autenticação.
            response.code() == 401 -> {
                Log.w(TAG, "Confirmação retornou 401 — janela já tinha fechado pelo último lote, desvinculando.")
                revocationHandler.revoke("Este tablet foi desativado pelo administrador. Insira um novo código de ativação.")
            }
            !response.isSuccessful -> {
                Log.w(TAG, "Confirmação de desativação recusada: HTTP ${response.code()}")
            }
        }
    }

    /** Consultada uma vez no startup do app (splash), antes de abrir a tela de ponto — cobre o
     *  caso de o tablet ter sido desligado/reiniciado sem nunca ter recebido o aviso pelos
     *  outros três canais, que só rodam com o app já em execução. */
    suspend fun verificarNoStartup() {
        val credentials = credentialsStore.read() ?: return
        val response = try {
            api.consultarDesativacao(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}"
            )
        } catch (e: IOException) {
            return
        }
        if (response.isSuccessful) {
            processar(response.body()?.desativacao)
        } else if (response.code() == 401) {
            // Já foi desativado E encerrado por completo (janela fechada) enquanto o app
            // estava totalmente fechado — as credenciais locais ficaram obsoletas sem
            // nenhum dos outros três canais ter tido chance de perceber.
            Log.w(TAG, "Consulta de desativação retornou 401 — já estava encerrado, desvinculando.")
            revocationHandler.revoke("Este tablet foi desativado pelo administrador. Insira um novo código de ativação.")
        }
    }

    /** Mesmo mecanismo do [RoomPunchRepository] — dispara o worker de sync em vez de esperar
     *  o próximo ciclo periódico, pra priorizar a drenagem assim que a desativação é vista. */
    private fun dispararSincronizacaoImediata() {
        val request = OneTimeWorkRequestBuilder<PunchSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PunchSyncWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private companion object {
        const val TAG = "DesativacaoHandler"
        const val MENSAGEM_PADRAO = "Tablet desativado"
    }
}
