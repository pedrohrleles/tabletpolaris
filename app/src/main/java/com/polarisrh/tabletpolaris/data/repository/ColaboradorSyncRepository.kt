package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorEntity
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService

/**
 * Puxa o roster de colaboradores (matrícula/cpf/nome/status) da empresa INTEIRA (todos os
 * estabelecimentos, não só o deste tablet) do Polaris RH e grava no banco local, paginando via
 * proximo_cursor até esgotar. Isso permite que um colaborador de outro local bata ponto aqui
 * (matrícula já reconhecida), mas ele precisa cadastrar a facial neste tablet específico na
 * primeira vez — o embedding é sempre local, o roster nunca carrega nem sobrescreve biometria.
 */
class ColaboradorSyncRepository(
    private val api: PolarisApiService,
    private val credentialsStore: DeviceCredentialsStore,
    private val colaboradorDao: ColaboradorDao
) {
    /**
     * Chamado a cada ativação bem-sucedida, antes de salvar as novas credenciais. Se o tablet
     * já tinha cache de colaboradores de uma ativação anterior:
     * - mesma empresa (reconexão após um erro que não foi o suporte desativando de propósito)
     *   → preserva o cache local, embeddings faciais inclusive;
     * - empresa diferente → zera tudo, já que matrícula/embedding não fazem sentido fora do
     *   contexto da empresa em que foram cadastrados.
     *
     * O roster de colaboradores agora é da empresa inteira, não mais filtrado por
     * estabelecimento (um colaborador pode substituir alguém em outro local e o tablet de lá
     * já reconhece a matrícula, só sem embedding facial até ele cadastrar naquele tablet
     * específico) — por isso a comparação aqui é por empresa.
     */
    suspend fun prepararParaAtivacao(novoIdEmpregador: String) {
        val idEmpregadorEmCache = credentialsStore.idEmpregadorColaboradoresCache()
        if (idEmpregadorEmCache != null && idEmpregadorEmCache != novoIdEmpregador) {
            colaboradorDao.limparTodos()
            credentialsStore.limparCacheColaboradores()
        }
        credentialsStore.salvarIdEmpregadorColaboradoresCache(novoIdEmpregador)
    }

    /** Carga completa — usada na ativação do tablet. */
    suspend fun sincronizarTudo() = sincronizar(desde = null)

    /**
     * Carga incremental — disparada pelo [DeviceStatusChecker] quando o dt_cadastro_alterado
     * do /status é mais recente que a última sincronização aplicada. Sem carga anterior
     * registrada, cai pra carga completa.
     */
    suspend fun sincronizarDelta() {
        val desde = credentialsStore.ultimaSincronizacaoColaboradores()
        sincronizar(desde)
    }

    private suspend fun sincronizar(desde: String?) {
        val credentials = credentialsStore.read() ?: return
        val bearerToken = "Bearer ${credentials.token}"

        var cursor: String? = null
        var dtSincronizacao: String? = null
        do {
            val response = api.listarColaboradores(
                idColetor = credentials.idColetor,
                bearerToken = bearerToken,
                desde = desde,
                cursor = cursor
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "Sync de colaboradores recusado: HTTP ${response.code()}")
                return
            }
            val body = response.body() ?: return
            dtSincronizacao = body.dtSincronizacao

            // fl_ativo=false representa um desligamento (mais comum na carga incremental) —
            // remove do cache local em vez de guardar como inativo.
            val (ativos, desligados) = body.colaboradores.partition { it.ativo }

            val entidadesAtivas = ativos.map { dto ->
                ColaboradorEntity(
                    matricula = dto.matricula,
                    cpf = dto.cpf,
                    nome = dto.nome,
                    ativo = dto.ativo,
                    atualizadoEm = dto.atualizadoEm,
                    embeddingFacial = colaboradorDao.buscarEmbedding(dto.matricula)
                )
            }
            colaboradorDao.upsertAll(entidadesAtivas)
            desligados.forEach { dto -> colaboradorDao.removerPorMatricula(dto.matricula) }

            cursor = body.proximoCursor
        } while (cursor != null)

        dtSincronizacao?.let { credentialsStore.salvarUltimaSincronizacaoColaboradores(it) }
    }

    private companion object {
        const val TAG = "ColaboradorSync"
    }
}
