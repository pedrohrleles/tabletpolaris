package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ColaboradorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(colaboradores: List<ColaboradorEntity>)

    @Query("SELECT * FROM colaborador WHERE matricula = :matricula")
    suspend fun buscarPorMatricula(matricula: String): ColaboradorEntity?

    @Query("SELECT * FROM colaborador ORDER BY matricula ASC")
    suspend fun listarTodos(): List<ColaboradorEntity>

    /** Base de comparação pro reconhecimento facial local — só quem já tem embedding salvo. */
    @Query("SELECT * FROM colaborador WHERE embeddingFacial IS NOT NULL")
    suspend fun listarComFacialCadastrada(): List<ColaboradorEntity>

    @Query("UPDATE colaborador SET embeddingFacial = :embedding WHERE matricula = :matricula")
    suspend fun salvarEmbedding(matricula: String, embedding: ByteArray)

    /** Menu de debug temporário na tela de reconhecimento — força o recadastro de um colaborador. */
    @Query("UPDATE colaborador SET embeddingFacial = NULL WHERE matricula = :matricula")
    suspend fun removerEmbedding(matricula: String)

    /** Usado ao sincronizar o roster: preserva o embedding já cadastrado localmente em vez de
     *  deixar o upsert (que sobrescreve a linha inteira) apagar o cadastro facial existente. */
    @Query("SELECT embeddingFacial FROM colaborador WHERE matricula = :matricula")
    suspend fun buscarEmbedding(matricula: String): ByteArray?

    @Query("SELECT COUNT(*) FROM colaborador")
    suspend fun contarColaboradores(): Int

    /** Dados pessoais: sempre apagados ao desvincular o tablet. Nunca mexe na fila de batidas. */
    @Query("DELETE FROM colaborador")
    suspend fun limparTodos()

    /** Usado no sync incremental quando um colaborador vem com fl_ativo=false (desligamento). */
    @Query("DELETE FROM colaborador WHERE matricula = :matricula")
    suspend fun removerPorMatricula(matricula: String)
}
