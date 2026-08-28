package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ColaboradorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(colaboradores: List<ColaboradorEntity>)

    @Query("SELECT * FROM rep_core_biometria_facial WHERE num_matricula = :matricula")
    suspend fun buscarPorMatricula(matricula: String): ColaboradorEntity?

    /** Usado na sincronização do roster pra evitar N consultas sequenciais (uma por
     *  colaborador) — busca o estado local de uma página inteira numa única query. */
    @Query("SELECT * FROM rep_core_biometria_facial WHERE num_matricula IN (:matriculas)")
    suspend fun buscarPorMatriculas(matriculas: List<String>): List<ColaboradorEntity>

    @Query("SELECT * FROM rep_core_biometria_facial ORDER BY num_matricula ASC")
    suspend fun listarTodos(): List<ColaboradorEntity>

    /** Base de comparação pro reconhecimento facial local — só quem já tem embedding salvo. */
    @Query("SELECT * FROM rep_core_biometria_facial WHERE embedding_tablet IS NOT NULL")
    suspend fun listarComFacialCadastrada(): List<ColaboradorEntity>

    @Query("UPDATE rep_core_biometria_facial SET embedding_tablet = :embedding WHERE num_matricula = :matricula")
    suspend fun salvarEmbedding(matricula: String, embedding: ByteArray)

    /** Menu de debug temporário na tela de reconhecimento — força o recadastro de um colaborador. */
    @Query("UPDATE rep_core_biometria_facial SET embedding_tablet = NULL WHERE num_matricula = :matricula")
    suspend fun removerEmbedding(matricula: String)

    /** Usado ao sincronizar o roster: preserva o embedding já cadastrado localmente em vez de
     *  deixar o upsert (que sobrescreve a linha inteira) apagar o cadastro facial existente. */
    @Query("SELECT embedding_tablet FROM rep_core_biometria_facial WHERE num_matricula = :matricula")
    suspend fun buscarEmbedding(matricula: String): ByteArray?

    @Query("SELECT COUNT(*) FROM rep_core_biometria_facial")
    suspend fun contarColaboradores(): Int

    /** Dados pessoais: sempre apagados ao desvincular o tablet. Nunca mexe na fila de batidas. */
    @Query("DELETE FROM rep_core_biometria_facial")
    suspend fun limparTodos()

    /** Usado no sync incremental quando um colaborador vem com fl_ativo=false (desligamento). */
    @Query("DELETE FROM rep_core_biometria_facial WHERE num_matricula = :matricula")
    suspend fun removerPorMatricula(matricula: String)

    /** Remoções de facial já aplicadas localmente (embedding apagado) mas ainda não
     *  confirmadas pro Polaris RH — inclui tanto as recém-detectadas nesta sincronização
     *  quanto sobras de tentativas de confirmação que falharam antes. Usa dt_cadastro_facial
     *  NULL (sem cadastro ativo) como sinal de que houve uma remoção pendente de aviso. */
    @Query("SELECT * FROM rep_core_biometria_facial WHERE dt_cadastro_facial IS NULL AND dt_remocao_confirmada IS NULL AND dt_reset_facial_aplicado IS NOT NULL")
    suspend fun listarComRemocaoPendenteDeConfirmacao(): List<ColaboradorEntity>

    @Query("UPDATE rep_core_biometria_facial SET dt_remocao_confirmada = :dtConfirmacao WHERE num_matricula = :matricula")
    suspend fun marcarRemocaoConfirmada(matricula: String, dtConfirmacao: String)

    /** Chamado assim que um cadastro facial novo é salvo localmente (Cadastrar Facial) — marca
     *  a confirmação pro Polaris RH como pendente de novo, mesmo que já tivesse sido confirmada
     *  antes (um recadastro é um evento novo). */
    @Query("UPDATE rep_core_biometria_facial SET dt_cadastro_facial = :dtCadastro, dt_cadastro_confirmado = NULL WHERE num_matricula = :matricula")
    suspend fun marcarCadastroFacial(matricula: String, dtCadastro: String)

    /** Cadastros feitos localmente mas ainda não confirmados pro Polaris RH (POST
     *  facial-cadastrada) — sem isso, o painel web mostra "Cadastre no Tablet" pra quem já
     *  cadastrou, e nunca oferece o botão de remover. */
    @Query("SELECT * FROM rep_core_biometria_facial WHERE dt_cadastro_facial IS NOT NULL AND dt_cadastro_confirmado IS NULL")
    suspend fun listarComCadastroPendenteDeConfirmacao(): List<ColaboradorEntity>

    @Query("UPDATE rep_core_biometria_facial SET dt_cadastro_confirmado = :dtConfirmacao WHERE num_matricula = :matricula")
    suspend fun marcarCadastroConfirmado(matricula: String, dtConfirmacao: String)
}
