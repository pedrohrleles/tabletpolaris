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

/** Renomeia tabelas e colunas pra padronizar com o banco do web (`rep_core_biometria_facial`,
 *  `rep_aud_biometria_log`, colunas em snake_case) — SQLite não tem um "ALTER TABLE RENAME
 *  COLUMN" confiável em todas as versões, então a técnica padrão é: cria a tabela nova já com
 *  o schema certo, copia os dados linha a linha (mapeando coluna antiga -> nova), apaga a
 *  antiga. Preserva tudo que já estava salvo, embeddings faciais inclusive. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rep_core_biometria_facial` (
                `matricula` TEXT NOT NULL PRIMARY KEY,
                `cpf` TEXT NOT NULL,
                `nome` TEXT NOT NULL,
                `ativo` INTEGER NOT NULL,
                `atualizado_em` TEXT NOT NULL,
                `embedding_facial` BLOB
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `rep_core_biometria_facial`
                (matricula, cpf, nome, ativo, atualizado_em, embedding_facial)
            SELECT matricula, cpf, nome, ativo, atualizadoEm, embeddingFacial FROM `colaborador`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `colaborador`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rep_aud_biometria_log` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT NOT NULL,
                `matricula` TEXT NOT NULL,
                `similaridade_calculada` REAL NOT NULL,
                `limiar_aplicado` REAL NOT NULL,
                `sucesso` INTEGER NOT NULL,
                `mensagem_erro` TEXT,
                `dt_tentativa` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `rep_aud_biometria_log`
                (id, id_empregador, matricula, similaridade_calculada, limiar_aplicado, sucesso, mensagem_erro, dt_tentativa)
            SELECT id, idEmpregador, matricula, similaridadeCalculada, limiarAplicado, sucesso, mensagemErro, dtTentativa
            FROM `tentativa_reconhecimento`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `tentativa_reconhecimento`")
    }
}

/** Mais um lote de renomeações de coluna pra fechar a padronização com o web (`matricula` ->
 *  `num_matricula` nas duas tabelas, `ativo` -> `fl_ativo` e `embedding_facial` ->
 *  `embedding_tablet` em `rep_core_biometria_facial`, `sucesso` -> `fl_sucesso` e
 *  `dt_tentativa` -> `tentativa_em` em `rep_aud_biometria_log`). Mesma técnica de sempre: cria
 *  a tabela com o schema novo num nome temporário, copia os dados, apaga a antiga, renomeia a
 *  nova pro nome final — preserva tudo que já estava salvo. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rep_core_biometria_facial_new` (
                `num_matricula` TEXT NOT NULL PRIMARY KEY,
                `cpf` TEXT NOT NULL,
                `nome` TEXT NOT NULL,
                `fl_ativo` INTEGER NOT NULL,
                `atualizado_em` TEXT NOT NULL,
                `embedding_tablet` BLOB
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `rep_core_biometria_facial_new`
                (num_matricula, cpf, nome, fl_ativo, atualizado_em, embedding_tablet)
            SELECT matricula, cpf, nome, ativo, atualizado_em, embedding_facial
            FROM `rep_core_biometria_facial`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `rep_core_biometria_facial`")
        db.execSQL("ALTER TABLE `rep_core_biometria_facial_new` RENAME TO `rep_core_biometria_facial`")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `rep_aud_biometria_log_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT NOT NULL,
                `num_matricula` TEXT NOT NULL,
                `similaridade_calculada` REAL NOT NULL,
                `limiar_aplicado` REAL NOT NULL,
                `fl_sucesso` INTEGER NOT NULL,
                `mensagem_erro` TEXT,
                `tentativa_em` TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `rep_aud_biometria_log_new`
                (id, id_empregador, num_matricula, similaridade_calculada, limiar_aplicado, fl_sucesso, mensagem_erro, tentativa_em)
            SELECT id, id_empregador, matricula, similaridade_calculada, limiar_aplicado, sucesso, mensagem_erro, dt_tentativa
            FROM `rep_aud_biometria_log`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `rep_aud_biometria_log`")
        db.execSQL("ALTER TABLE `rep_aud_biometria_log_new` RENAME TO `rep_aud_biometria_log`")
    }
}

/** `batida_pendente` reestruturada em `batidas_sincronizadas` — deixa de ser só a fila offline
 *  (que exigia apagar a linha depois de sincronizar) e passa a guardar o histórico completo de
 *  TODAS as batidas do tablet; "fila offline" agora é só o filtro `fl_sincronizado = 0` sobre
 *  essa mesma tabela (padrão outbox). Também adiciona as colunas de identificação vindas do
 *  backend (id_empregador, id_vinculo, id_empregado, nr_cpf_empregado — ainda sem fonte de
 *  dado definida, ficam nulas por enquanto) e as de rastreio de tentativa de sincronização. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `batidas_sincronizadas_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT,
                `id_vinculo` TEXT,
                `id_empregado` TEXT,
                `num_matricula` TEXT NOT NULL,
                `nr_cpf_empregado` TEXT,
                `dt_hr_marcacao` TEXT NOT NULL,
                `fl_sincronizado` INTEGER NOT NULL DEFAULT 0,
                `qtd_tentativas_sincronizacao` INTEGER NOT NULL DEFAULT 0,
                `dt_ultima_tentativa` TEXT,
                `mensagem_erro_sincronizacao` TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `batidas_sincronizadas_new`
                (id, num_matricula, dt_hr_marcacao, fl_sincronizado, qtd_tentativas_sincronizacao)
            SELECT id, matricula, dtHora, 0, 0 FROM `batida_pendente`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `batida_pendente`")
        db.execSQL("ALTER TABLE `batidas_sincronizadas_new` RENAME TO `batidas_sincronizadas`")
    }
}

/** Remove `id_vinculo` e `id_empregado` de `batidas_sincronizadas` — decidido que não serão
 *  usados. `nr_cpf_empregado` fica (já temos essa informação disponível localmente). */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `batidas_sincronizadas_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT,
                `num_matricula` TEXT NOT NULL,
                `nr_cpf_empregado` TEXT,
                `dt_hr_marcacao` TEXT NOT NULL,
                `fl_sincronizado` INTEGER NOT NULL DEFAULT 0,
                `qtd_tentativas_sincronizacao` INTEGER NOT NULL DEFAULT 0,
                `dt_ultima_tentativa` TEXT,
                `mensagem_erro_sincronizacao` TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `batidas_sincronizadas_new`
                (id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 fl_sincronizado, qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao)
            SELECT id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 fl_sincronizado, qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao
            FROM `batidas_sincronizadas`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `batidas_sincronizadas`")
        db.execSQL("ALTER TABLE `batidas_sincronizadas_new` RENAME TO `batidas_sincronizadas`")
    }
}

/** Adiciona as colunas que o contrato real de POST rep-p/dispositivos/marcacoes exige por
 *  marcação: `id_local` (UUID de idempotência), `assinatura` (ECDSA sobre
 *  id_coletor|id_local|num_matricula|dt_hr_marcacao) e a métrica de auditoria (`nr_score`/
 *  `nr_threshold`). A tabela nunca teve uma implementação real de escrita até agora (só o
 *  `FakePunchRepository`), então não existem linhas de verdade para migrar — os defaults abaixo
 *  são só para satisfazer o NOT NULL do SQLite. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `batidas_sincronizadas_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT,
                `num_matricula` TEXT NOT NULL,
                `nr_cpf_empregado` TEXT,
                `dt_hr_marcacao` TEXT NOT NULL,
                `id_local` TEXT NOT NULL DEFAULT '',
                `assinatura` TEXT NOT NULL DEFAULT '',
                `nr_score` REAL NOT NULL DEFAULT 0,
                `nr_threshold` REAL NOT NULL DEFAULT 0,
                `fl_sincronizado` INTEGER NOT NULL DEFAULT 0,
                `qtd_tentativas_sincronizacao` INTEGER NOT NULL DEFAULT 0,
                `dt_ultima_tentativa` TEXT,
                `mensagem_erro_sincronizacao` TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `batidas_sincronizadas_new`
                (id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 fl_sincronizado, qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao)
            SELECT id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 fl_sincronizado, qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao
            FROM `batidas_sincronizadas`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `batidas_sincronizadas`")
        db.execSQL("ALTER TABLE `batidas_sincronizadas_new` RENAME TO `batidas_sincronizadas`")
    }
}

/** Troca `fl_sincronizado` (booleano) por `status_sincronizacao` (PENDENTE/SINCRONIZADA/
 *  REJEITADA) — o backend confirmou que o 201 do lote não significa "tudo aceito": cada
 *  marcação é processada isoladamente e pode vir rejeitada em definitivo (matrícula desligada,
 *  sem escala vigente, assinatura inválida). Um booleano não tem como distinguir "ainda
 *  pendente" de "rejeitada pra sempre" sem continuar reaparecendo em listarPendentes(). */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `batidas_sincronizadas_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `id_empregador` TEXT,
                `num_matricula` TEXT NOT NULL,
                `nr_cpf_empregado` TEXT,
                `dt_hr_marcacao` TEXT NOT NULL,
                `id_local` TEXT NOT NULL DEFAULT '',
                `assinatura` TEXT NOT NULL DEFAULT '',
                `nr_score` REAL NOT NULL DEFAULT 0,
                `nr_threshold` REAL NOT NULL DEFAULT 0,
                `status_sincronizacao` TEXT NOT NULL DEFAULT 'PENDENTE',
                `qtd_tentativas_sincronizacao` INTEGER NOT NULL DEFAULT 0,
                `dt_ultima_tentativa` TEXT,
                `mensagem_erro_sincronizacao` TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `batidas_sincronizadas_new`
                (id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 id_local, assinatura, nr_score, nr_threshold, status_sincronizacao,
                 qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao)
            SELECT id, id_empregador, num_matricula, nr_cpf_empregado, dt_hr_marcacao,
                 id_local, assinatura, nr_score, nr_threshold,
                 CASE WHEN fl_sincronizado = 1 THEN 'SINCRONIZADA' ELSE 'PENDENTE' END,
                 qtd_tentativas_sincronizacao, dt_ultima_tentativa, mensagem_erro_sincronizacao
            FROM `batidas_sincronizadas`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `batidas_sincronizadas`")
        db.execSQL("ALTER TABLE `batidas_sincronizadas_new` RENAME TO `batidas_sincronizadas`")
    }
}
