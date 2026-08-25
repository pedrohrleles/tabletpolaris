package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BatidaDao {

    @Insert
    suspend fun inserir(batida: BatidaEntity): Long

    /** Fila offline: tudo que ainda não foi confirmado como recebido pelo Polaris RH. */
    @Query("SELECT * FROM batidas_sincronizadas WHERE fl_sincronizado = 0 ORDER BY dt_hr_marcacao ASC")
    suspend fun listarPendentes(): List<BatidaEntity>

    @Query("SELECT COUNT(*) FROM batidas_sincronizadas WHERE fl_sincronizado = 0")
    suspend fun contarPendentes(): Int

    /** Histórico completo (sincronizadas ou não) — usado na tela de debug. */
    @Query("SELECT * FROM batidas_sincronizadas ORDER BY dt_hr_marcacao DESC")
    suspend fun listarTodas(): List<BatidaEntity>
}
