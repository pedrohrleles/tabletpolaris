package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ColaboradorEntity::class, BatidaPendenteEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PolarisDatabase : RoomDatabase() {
    abstract fun colaboradorDao(): ColaboradorDao
    abstract fun batidaPendenteDao(): BatidaPendenteDao
}
