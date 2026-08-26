package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ColaboradorEntity::class, BatidaEntity::class, TentativaReconhecimentoEntity::class],
    version = 13,
    exportSchema = false
)
abstract class PolarisDatabase : RoomDatabase() {
    abstract fun colaboradorDao(): ColaboradorDao
    abstract fun batidaDao(): BatidaDao
    abstract fun tentativaReconhecimentoDao(): TentativaReconhecimentoDao
}
