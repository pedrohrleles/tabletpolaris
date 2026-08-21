package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BatidaPendenteDao {

    @Insert
    suspend fun inserir(batida: BatidaPendenteEntity): Long

    @Query("SELECT * FROM batida_pendente ORDER BY dtHora ASC")
    suspend fun listarPendentes(): List<BatidaPendenteEntity>

    @Query("SELECT COUNT(*) FROM batida_pendente")
    suspend fun contarPendentes(): Int

    @Query("DELETE FROM batida_pendente WHERE id = :id")
    suspend fun removerPorId(id: Long)
}
