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
}
