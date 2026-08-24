package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Espelha a rep_aud_biometria_log do web (id, id_empregador, id_empregado, distância
 * calculada, threshold_aplicado, fl_sucesso, mensagem_erro) — só que aqui "distância" é na
 * verdade [similaridadeCalculada] (cosseno, não euclidiana como no dlib do web): quanto MAIOR,
 * mais parecido. Registrada a cada tentativa de reconhecimento (bater ponto), sucesso ou não —
 * serve pra calibrar o limiar com dados reais em vez de chutar.
 */
@Entity(tableName = "tentativa_reconhecimento")
data class TentativaReconhecimentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val idEmpregador: String,
    val matricula: String,
    val similaridadeCalculada: Float,
    val limiarAplicado: Float,
    val sucesso: Boolean,
    val mensagemErro: String?,
    val dtTentativa: String
)
