package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Só adiciona a tabela de auditoria de reconhecimento — não mexe em colaborador/batida_pendente,
 *  então embeddings já cadastrados sobrevivem à atualização do app. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `tentativa_reconhecimento` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `idEmpregador` TEXT NOT NULL,
                `matricula` TEXT NOT NULL,
                `similaridadeCalculada` REAL NOT NULL,
                `limiarAplicado` REAL NOT NULL,
                `sucesso` INTEGER NOT NULL,
                `mensagemErro` TEXT,
                `dtTentativa` TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}
