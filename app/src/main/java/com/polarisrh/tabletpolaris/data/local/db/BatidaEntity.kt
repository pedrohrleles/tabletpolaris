package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** PENDENTE = ainda não confirmada pelo Polaris RH (fila offline). SINCRONIZADA = aceita ou
 *  reconhecida como duplicada pelo servidor. REJEITADA = recusada definitivamente (matrícula
 *  desligada, sem escala vigente, assinatura inválida) — motivo fica em [BatidaEntity
 *  .mensagemErroSincronizacao]; reenviar não muda o resultado, então também sai da fila. */
object StatusSincronizacao {
    const val PENDENTE = "PENDENTE"
    const val SINCRONIZADA = "SINCRONIZADA"
    const val REJEITADA = "REJEITADA"
}

/**
 * Histórico completo de TODAS as batidas registradas neste tablet — a "fila offline" não é
 * uma tabela separada, é só o filtro `status_sincronizacao = 'PENDENTE'` sobre essa mesma
 * tabela (padrão "outbox": grava local na hora, sincroniza em segundo plano depois, sem nunca
 * travar a tela de "Bater Ponto" esperando rede).
 *
 * [idEmpregador] ainda não tem fonte de dado definida no app (coluna adicionada agora pra já
 * deixar o schema pronto, ver com o time do backend/RH de onde vem antes de popular de
 * verdade). [cpfEmpregado] já temos disponível localmente (é o mesmo CPF que já vem no roster
 * de colaboradores).
 *
 * [idLocal], [assinatura], [nrScore] e [nrThreshold] são o que POST rep-p/dispositivos/marcacoes
 * espera por marcação — gerados uma única vez na hora da batida (nunca recalculados num retry
 * de sincronização, pra manter a assinatura estável; o backend confirmou que [idLocal] só serve
 * pra correlacionar a resposta com essa linha, a deduplicação de verdade é por
 * colaborador+coletor+instante).
 */
@Entity(tableName = "batidas_sincronizadas")
data class BatidaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "id_empregador") val idEmpregador: String?,
    @ColumnInfo(name = "num_matricula") val matricula: String,
    @ColumnInfo(name = "nr_cpf_empregado") val cpfEmpregado: String?,
    @ColumnInfo(name = "dt_hr_marcacao") val dtHrMarcacao: String,
    @ColumnInfo(name = "id_local") val idLocal: String,
    val assinatura: String,
    @ColumnInfo(name = "nr_score") val nrScore: Float,
    @ColumnInfo(name = "nr_threshold") val nrThreshold: Float,
    @ColumnInfo(name = "status_sincronizacao") val statusSincronizacao: String = StatusSincronizacao.PENDENTE,
    @ColumnInfo(name = "qtd_tentativas_sincronizacao") val qtdTentativasSincronizacao: Int = 0,
    @ColumnInfo(name = "dt_ultima_tentativa") val dtUltimaTentativa: String? = null,
    @ColumnInfo(name = "mensagem_erro_sincronizacao") val mensagemErroSincronizacao: String? = null
)
