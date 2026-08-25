package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Histórico completo de TODAS as batidas registradas neste tablet, sincronizadas ou não — a
 * "fila offline" não é mais uma tabela separada, é só o filtro `fl_sincronizado = 0` sobre essa
 * mesma tabela (padrão "outbox": grava local na hora, sincroniza em segundo plano depois, sem
 * nunca travar a tela de "Bater Ponto" esperando rede).
 *
 * [idEmpregador] ainda não tem fonte de dado definida no app (coluna adicionada agora pra já
 * deixar o schema pronto, ver com o time do backend/RH de onde vem antes de popular de
 * verdade). [cpfEmpregado] já temos disponível localmente (é o mesmo CPF que já vem no roster
 * de colaboradores).
 */
@Entity(tableName = "batidas_sincronizadas")
data class BatidaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "id_empregador") val idEmpregador: String?,
    @ColumnInfo(name = "num_matricula") val matricula: String,
    @ColumnInfo(name = "nr_cpf_empregado") val cpfEmpregado: String?,
    @ColumnInfo(name = "dt_hr_marcacao") val dtHrMarcacao: String,
    /** true assim que essa batida foi confirmada como recebida pelo Polaris RH — até lá, faz
     *  parte da fila offline (`fl_sincronizado = 0`). */
    @ColumnInfo(name = "fl_sincronizado") val sincronizado: Boolean = false,
    @ColumnInfo(name = "qtd_tentativas_sincronizacao") val qtdTentativasSincronizacao: Int = 0,
    @ColumnInfo(name = "dt_ultima_tentativa") val dtUltimaTentativa: String? = null,
    @ColumnInfo(name = "mensagem_erro_sincronizacao") val mensagemErroSincronizacao: String? = null
)
