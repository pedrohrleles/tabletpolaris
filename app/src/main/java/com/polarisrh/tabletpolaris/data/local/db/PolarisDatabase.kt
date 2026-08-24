package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ColaboradorEntity::class, BatidaPendenteEntity::class, TentativaReconhecimentoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class PolarisDatabase : RoomDatabase() {
    abstract fun colaboradorDao(): ColaboradorDao
    abstract fun batidaPendenteDao(): BatidaPendenteDao
    abstract fun tentativaReconhecimentoDao(): TentativaReconhecimentoDao
}
