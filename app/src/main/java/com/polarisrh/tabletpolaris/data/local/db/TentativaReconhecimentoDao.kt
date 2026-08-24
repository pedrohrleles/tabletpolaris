package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TentativaReconhecimentoDao {

    @Insert
    suspend fun inserir(tentativa: TentativaReconhecimentoEntity)

    @Query("SELECT * FROM rep_aud_biometria_log ORDER BY id DESC")
    suspend fun listarTodas(): List<TentativaReconhecimentoEntity>

    @Query("SELECT * FROM rep_aud_biometria_log WHERE num_matricula = :matricula ORDER BY id DESC")
    suspend fun listarPorMatricula(matricula: String): List<TentativaReconhecimentoEntity>
}
