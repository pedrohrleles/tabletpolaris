package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Espelha 1:1 os campos de GET /coletores/{idColetor}/colaboradores (nr_matricula, nr_cpf,
 * nm_colaborador, fl_ativo, atualizado_em) — exceto [embeddingFacial], que nunca vem do
 * backend: só é preenchido localmente quando o colaborador faz "Cadastrar Facial" neste
 * tablet, e nunca é enviado de volta pro servidor. Nome da tabela e das colunas (snake_case)
 * padronizados com o `rep_core_biometria_facial` do web — os nomes dos campos em Kotlin
 * continuam em camelCase (convenção normal da linguagem), só o nome da coluna no banco muda.
 */
@Entity(tableName = "rep_core_biometria_facial")
data class ColaboradorEntity(
    @ColumnInfo(name = "num_matricula") @PrimaryKey val matricula: String,
    val cpf: String,
    val nome: String,
    @ColumnInfo(name = "fl_ativo") val ativo: Boolean,
    @ColumnInfo(name = "atualizado_em") val atualizadoEm: String,
    @ColumnInfo(name = "embedding_tablet") val embeddingFacial: ByteArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColaboradorEntity) return false
        return matricula == other.matricula &&
            cpf == other.cpf &&
            nome == other.nome &&
            ativo == other.ativo &&
            atualizadoEm == other.atualizadoEm &&
            (embeddingFacial?.contentEquals(other.embeddingFacial ?: ByteArray(0)) ?: (other.embeddingFacial == null))
    }

    override fun hashCode(): Int {
        var result = matricula.hashCode()
        result = 31 * result + cpf.hashCode()
        result = 31 * result + nome.hashCode()
        result = 31 * result + ativo.hashCode()
        result = 31 * result + atualizadoEm.hashCode()
        result = 31 * result + (embeddingFacial?.contentHashCode() ?: 0)
        return result
    }
}
