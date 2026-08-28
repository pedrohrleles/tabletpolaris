package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorEntity
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.remote.dto.ColaboradorDto
import com.polarisrh.tabletpolaris.data.remote.dto.FacialCadastradaRequest
import com.polarisrh.tabletpolaris.data.remote.dto.FacialNotificacaoResponse
import com.polarisrh.tabletpolaris.data.remote.dto.FacialRemovidaRequest
import com.polarisrh.tabletpolaris.data.remote.dto.RemocaoFacialDto
import java.io.IOException
import java.time.Instant

/**
 * Puxa o roster de colaboradores (matrícula/cpf/nome/status) da empresa INTEIRA (todos os
 * estabelecimentos, não só o deste tablet) do Polaris RH e grava no banco local, paginando via
 * proximo_cursor até esgotar. Isso permite que um colaborador de outro local bata ponto aqui
 * (matrícula já reconhecida), mas ele precisa cadastrar a facial neste tablet específico na
 * primeira vez — o embedding é sempre local, o roster nunca carrega nem sobrescreve biometria.
 *
 * Também fecha o ciclo de facial com o painel web (nunca recebe foto/embedding, só os EVENTOS):
 * cadastro local → POST facial-cadastrada; pedido de reset (dt_reset_facial) → apaga local →
 * POST facial-removida.
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

    /**
     * Carga completa — sempre pede o estado atual de TODOS os colaboradores, nunca um delta
     * filtrado por "o que mudou desde X". Chamada tanto na ativação quanto incondicionalmente
     * a cada polling de 30s da tela de ponto e a cada heartbeat de 15min.
     *
     * Decisão deliberada: o endpoint incremental (`desde=`) existe, mas o filtro que o backend
     * usa pra decidir o que incluir depende de `atualizado_em`/campos equivalentes que já se
     * provaram não confiáveis (desligamento não atualiza `rep_core_vinculo.atualizado_em`; o
     * mesmo padrão de bug já apareceu também no `dt_cadastro_alterado`).
     *
     * Confirmado por teste ao vivo: mesmo a carga COMPLETA nunca inclui desligados — nem com
     * `fl_ativo: false`, eles são simplesmente omitidos da resposta. Por isso a remoção não
     * pode depender de um item vir marcado como inativo; em vez disso, é por RECONCILIAÇÃO —
     * qualquer matrícula que já existia localmente mas não veio nesta carga completa não
     * pertence mais à empresa (desligado ou qualquer outro motivo) e é removida. Como toda
     * chamada aqui é sempre uma foto completa (nunca parcial), essa comparação é segura.
     */
    suspend fun sincronizarTudo() {
        val credentials = credentialsStore.read() ?: return
        val bearerToken = "Bearer ${credentials.token}"

        var cursor: String? = null
        val matriculasRecebidas = mutableSetOf<String>()
        do {
            val response = api.listarColaboradores(
                idColetor = credentials.idColetor,
                bearerToken = bearerToken,
                cursor = cursor
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "Sync de colaboradores recusado: HTTP ${response.code()}")
                return
            }
            val body = response.body() ?: return

            // Isento (dispensado de bater ponto) vem primeiro, ANTES do particionamento por
            // fl_ativo — o payload de isento traz fl_ativo=false (reuso do campo pra "recusar
            // batida"), mas a pessoa continua com vínculo em ordem, então nunca deve cair no
            // fluxo de desligamento (que apaga tudo).
            val (isentos, resto) = body.colaboradores.partition { it.isento }
            // fl_ativo=false, fora dos isentos, na prática nunca aparece (desligados são
            // omitidos, não marcados) — trata o caso mesmo assim, caso o backend passe a
            // mandar explicitamente.
            val (ativos, desligados) = resto.partition { it.ativo }
            matriculasRecebidas += ativos.map { it.matricula }
            matriculasRecebidas += isentos.map { it.matricula }

            // Uma única query pra página inteira, em vez de uma consulta por colaborador
            // (evita competir com leituras da tela de Bater Ponto no meio da sincronização).
            val existentesDaPagina = colaboradorDao
                .buscarPorMatriculas((ativos + isentos).map { it.matricula })
                .associateBy { it.matricula }
            val entidades = (ativos + isentos).map { dto -> paraEntidade(dto, existentesDaPagina[dto.matricula]) }
            colaboradorDao.upsertAll(entidades)
            desligados.forEach { dto -> colaboradorDao.removerPorMatricula(dto.matricula) }

            cursor = body.proximoCursor
        } while (cursor != null)

        // Reconciliação: quem estava aqui antes mas não veio nesta carga completa não pertence
        // mais à empresa — é assim que um desligamento (omitido, nunca marcado) é detectado.
        val matriculasLocais = colaboradorDao.listarTodos().map { it.matricula }
        matriculasLocais.filterNot { it in matriculasRecebidas }.forEach { matricula ->
            Log.i(TAG, "Matrícula $matricula não veio na carga completa — removendo do tablet")
            colaboradorDao.removerPorMatricula(matricula)
        }

        confirmarCadastrosPendentes()
        confirmarRemocoesPendentes()
    }

    /** Monta a entidade local a partir do DTO — comum a colaboradores normais e isentos.
     *  [existente] vem de uma busca em lote feita antes, pra página inteira (ver
     *  sincronizarTudo) — evita uma consulta ao banco por colaborador. */
    private fun paraEntidade(dto: ColaboradorDto, existente: ColaboradorEntity?): ColaboradorEntity {
        // Regra do backend: um reset só se aplica se for POSTERIOR ao cadastro local atual
        // (evita que um pedido de reset antigo apague um cadastro mais novo) — e só faz
        // sentido se já existir embedding pra remover (senão um dt_reset_facial "perdido" pra
        // alguém que nunca cadastrou dispararia uma remoção fantasma).
        val resetPendente = dto.dtResetFacial != null &&
            existente?.embeddingFacial != null &&
            (
                existente.dtCadastroFacial == null ||
                    Instant.parse(dto.dtResetFacial) > Instant.parse(existente.dtCadastroFacial)
                )
        if (resetPendente) {
            Log.i(TAG, "Facial resetada remotamente — matrícula=${dto.matricula}, recadastro necessário neste tablet")
        }
        return ColaboradorEntity(
            matricula = dto.matricula,
            // Nulos só acontecem pra quem é isento (o backend não manda CPF/nome nesse caso).
            cpf = dto.cpf ?: "",
            nome = dto.nome ?: "",
            // Isento vem com fl_ativo=false no payload (reuso do campo pra "recusar batida"),
            // mas o vínculo é ativo de verdade — nunca deixa cair no fluxo de desligamento.
            ativo = if (dto.isento) true else dto.ativo,
            atualizadoEm = dto.atualizadoEm,
            embeddingFacial = if (resetPendente) null else existente?.embeddingFacial,
            dtCadastroFacial = if (resetPendente) null else existente?.dtCadastroFacial,
            dtCadastroConfirmado = if (resetPendente) null else existente?.dtCadastroConfirmado,
            // Só atualizado quando um reset É de fato aplicado — usado só pra saber que "esse
            // colaborador teve uma remoção real, pendente de confirmar" (ver
            // listarComRemocaoPendenteDeConfirmacao), nunca pra decidir se aplica ou não.
            dtResetFacialAplicado = if (resetPendente) dto.dtResetFacial else existente?.dtResetFacialAplicado,
            dtRemocaoConfirmada = if (resetPendente) null else existente?.dtRemocaoConfirmada,
            isento = dto.isento
        )
    }

    /**
     * Avisa o Polaris RH que uma facial foi cadastrada localmente — é o que faz o colaborador
     * ver "Facial Cadastrada" (com botão de remover) no painel web em vez de continuar preso em
     * "Cadastre no Tablet". Best-effort: se falhar, a próxima sincronização tenta de novo, já
     * que dt_cadastro_confirmado continua null.
     */
    private suspend fun confirmarCadastrosPendentes() {
        val pendentes = colaboradorDao.listarComCadastroPendenteDeConfirmacao()
        if (pendentes.isEmpty()) return

        val credentials = credentialsStore.read() ?: return
        val request = FacialCadastradaRequest(
            cadastros = pendentes.map { RemocaoFacialDto(nrMatricula = it.matricula, dtRemocao = it.dtCadastroFacial ?: Instant.now().toString()) }
        )

        try {
            val response = api.confirmarFacialCadastrada(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}",
                request = request
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                aplicarConfirmacoes(body, "cadastro") { matricula, dt -> colaboradorDao.marcarCadastroConfirmado(matricula, dt) }
            } else {
                Log.w(TAG, "Confirmação de cadastro facial recusada: HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Confirmação de cadastro facial sem conexão, tentando de novo na próxima sincronização: ${e.message}")
        }
    }

    /**
     * Avisa o Polaris RH que uma facial foi de fato apagada localmente (por causa de um
     * dt_reset_facial) — é o que faz o colaborador ver "Facial Removida" no painel web em vez
     * de ficar sem saber se o pedido foi cumprido. Best-effort: se falhar, a próxima
     * sincronização tenta de novo, já que dt_remocao_confirmada continua null.
     */
    private suspend fun confirmarRemocoesPendentes() {
        val pendentes = colaboradorDao.listarComRemocaoPendenteDeConfirmacao()
        if (pendentes.isEmpty()) return

        val credentials = credentialsStore.read() ?: return
        val agora = Instant.now().toString()
        val request = FacialRemovidaRequest(
            remocoes = pendentes.map { RemocaoFacialDto(nrMatricula = it.matricula, dtRemocao = agora) }
        )

        try {
            val response = api.confirmarFacialRemovida(
                idColetor = credentials.idColetor,
                bearerToken = "Bearer ${credentials.token}",
                request = request
            )
            val body = response.body()
            if (response.isSuccessful && body != null) {
                aplicarConfirmacoes(body, "remoção") { matricula, dt -> colaboradorDao.marcarRemocaoConfirmada(matricula, dt) }
            } else {
                Log.w(TAG, "Confirmação de remoção facial recusada: HTTP ${response.code()}")
            }
        } catch (e: IOException) {
            Log.w(TAG, "Confirmação de remoção facial sem conexão, tentando de novo na próxima sincronização: ${e.message}")
        }
    }

    /** Comum às duas confirmações: marca as aceitas, e também as rejeitadas (motivo é
     *  definitivo — matrícula não existe mais na empresa, por exemplo — reenviar não muda o
     *  resultado, então não fica tentando pra sempre). */
    private suspend fun aplicarConfirmacoes(
        body: FacialNotificacaoResponse,
        rotulo: String,
        marcar: suspend (matricula: String, dt: String) -> Unit
    ) {
        val agora = Instant.now().toString()
        body.confirmadas.forEach { marcar(it.nrMatricula, agora) }
        body.rejeitadas.forEach {
            Log.w(TAG, "Notificação de $rotulo facial rejeitada — matrícula=${it.nrMatricula} motivo=${it.motivo}")
            marcar(it.nrMatricula, agora)
        }
        if (body.confirmadas.isNotEmpty()) {
            Log.i(TAG, "Notificação de $rotulo facial confirmada pro Polaris RH — ${body.confirmadas.size} matrícula(s)")
        }
    }

    private companion object {
        const val TAG = "ColaboradorSync"
    }
}
