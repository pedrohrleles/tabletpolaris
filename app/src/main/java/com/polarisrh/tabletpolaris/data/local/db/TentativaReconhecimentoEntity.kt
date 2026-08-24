package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Espelha a rep_aud_biometria_log do web (id, id_empregador, id_empregado, distância
 * calculada, threshold_aplicado, fl_sucesso, mensagem_erro) — só que aqui "distância" é na
 * verdade [similaridadeCalculada] (cosseno, não euclidiana como no dlib do web): quanto MAIOR,
 * mais parecido. Registrada a cada tentativa de reconhecimento (bater ponto), sucesso ou não —
 * serve pra calibrar o limiar com dados reais em vez de chutar. Nome da tabela e das colunas
 * (snake_case) padronizados com o web — os nomes dos campos em Kotlin continuam em camelCase,
 * só o nome da coluna no banco muda.
 */
@Entity(tableName = "rep_aud_biometria_log")
data class TentativaReconhecimentoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "id_empregador") val idEmpregador: String,
    @ColumnInfo(name = "num_matricula") val matricula: String,
    @ColumnInfo(name = "similaridade_calculada") val similaridadeCalculada: Float,
    @ColumnInfo(name = "limiar_aplicado") val limiarAplicado: Float,
    @ColumnInfo(name = "fl_sucesso") val sucesso: Boolean,
    @ColumnInfo(name = "mensagem_erro") val mensagemErro: String?,
    @ColumnInfo(name = "tentativa_em") val dtTentativa: String
)
