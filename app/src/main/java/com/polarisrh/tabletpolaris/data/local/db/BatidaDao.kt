package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface BatidaDao {

    @Insert
    suspend fun inserir(batida: BatidaEntity): Long

    /** Fila offline: tudo que ainda não foi confirmado (aceito, duplicado ou rejeitado) pelo
     *  Polaris RH. */
    @Query("SELECT * FROM batidas_sincronizadas WHERE status_sincronizacao = 'PENDENTE' ORDER BY dt_hr_marcacao ASC")
    suspend fun listarPendentes(): List<BatidaEntity>

    @Query("SELECT COUNT(*) FROM batidas_sincronizadas WHERE status_sincronizacao = 'PENDENTE'")
    suspend fun contarPendentes(): Int


    @Query(
        """
        UPDATE batidas_sincronizadas
        SET status_sincronizacao = 'SINCRONIZADA', dt_ultima_tentativa = :dtSincronizacao, mensagem_erro_sincronizacao = NULL
        WHERE id = :id
        """
    )
    suspend fun marcarComoSincronizado(id: Long, dtSincronizacao: String)

    /** Recusa definitiva do servidor (matrícula desligada, sem escala vigente, assinatura
     *  inválida) — sai da fila (reenviar não muda o resultado), mas o motivo fica registrado
     *  pra consulta posterior. */
    @Query(
        """
        UPDATE batidas_sincronizadas
        SET status_sincronizacao = 'REJEITADA', dt_ultima_tentativa = :dtTentativa, mensagem_erro_sincronizacao = :motivo
        WHERE id = :id
        """
    )
    suspend fun marcarComoRejeitada(id: Long, dtTentativa: String, motivo: String)

    @Query(
        """
        UPDATE batidas_sincronizadas
        SET qtd_tentativas_sincronizacao = qtd_tentativas_sincronizacao + 1,
            dt_ultima_tentativa = :dtTentativa,
            mensagem_erro_sincronizacao = :mensagemErro
        WHERE id = :id
        """
    )
    suspend fun registrarFalhaSincronizacao(id: Long, dtTentativa: String, mensagemErro: String)

    /** Candidatas ao reenvio automático de rejeitadas por falta de escala (ver
     *  [com.polarisrh.tabletpolaris.data.repository.PunchSyncRepository.reenviarPorFaltaDeEscalaSeNecessario])
     *  — comparação por texto EXATO com o motivo, combinado com o backend que essa mensagem
     *  nunca muda. Qualquer outro motivo de rejeição (ex.: "Marcação com data futura") nunca
     *  entra aqui, de propósito. */
    @Query("SELECT * FROM batidas_sincronizadas WHERE status_sincronizacao = 'REJEITADA' AND mensagem_erro_sincronizacao = :motivo")
    suspend fun listarRejeitadasPorMotivo(motivo: String): List<BatidaEntity>
}
