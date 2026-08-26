package com.polarisrh.tabletpolaris.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Espelha 1:1 os campos de GET /coletores/{idColetor}/colaboradores (nr_matricula, nr_cpf,
 * nm_colaborador, fl_ativo, atualizado_em) — exceto [embeddingFacial], que nunca vem do
 * backend: só é preenchido localmente quando o colaborador faz "Cadastrar Facial" neste
 * tablet, e nunca é enviado de volta pro servidor (só a foto/embedding em si — o FATO de ter
 * cadastrado e removido é avisado via POST facial-cadastrada/facial-removida). Nome da tabela
 * e das colunas (snake_case) padronizados com o `rep_core_biometria_facial` do web — os nomes
 * dos campos em Kotlin continuam em camelCase (convenção normal da linguagem), só o nome da
 * coluna no banco muda.
 */
@Entity(tableName = "rep_core_biometria_facial")
data class ColaboradorEntity(
    @ColumnInfo(name = "num_matricula") @PrimaryKey val matricula: String,
    val cpf: String,
    val nome: String,
    @ColumnInfo(name = "fl_ativo") val ativo: Boolean,
    @ColumnInfo(name = "atualizado_em") val atualizadoEm: String,
    @ColumnInfo(name = "embedding_tablet") val embeddingFacial: ByteArray? = null,
    /** Quando o cadastro facial ATUAL foi feito neste tablet (null = sem facial cadastrada).
     *  É a referência que o backend usa: um dt_reset_facial só é aplicado se for posterior a
     *  esta data — evita que um pedido de reset antigo apague um cadastro mais novo. */
    @ColumnInfo(name = "dt_cadastro_facial") val dtCadastroFacial: String? = null,
    /** Null enquanto o cadastro acima ainda não foi confirmado pro Polaris RH (POST
     *  facial-cadastrada) — é o que faz o colaborador ver "Facial Cadastrada" (com botão de
     *  remover) no painel web em vez de continuar preso em "Cadastre no Tablet". */
    @ColumnInfo(name = "dt_cadastro_confirmado") val dtCadastroConfirmado: String? = null,
    /** Último dt_reset_facial visto (só pra auditoria/debug — a decisão de aplicar o reset usa
     *  [dtCadastroFacial], não este campo). */
    @ColumnInfo(name = "dt_reset_facial_aplicado") val dtResetFacialAplicado: String? = null,
    /** Null enquanto a remoção local ainda não foi confirmada pro Polaris RH (POST
     *  facial-removida) — é o que faz o colaborador ver "Facial Removida" no painel web em vez
     *  de ficar sem saber se o pedido foi cumprido. Preenchido só depois da confirmação ter
     *  sido aceita pelo backend; se o POST falhar, fica null e é tentado de novo na próxima
     *  sincronização. */
    @ColumnInfo(name = "dt_remocao_confirmada") val dtRemocaoConfirmada: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ColaboradorEntity) return false
        return matricula == other.matricula &&
            cpf == other.cpf &&
            nome == other.nome &&
            ativo == other.ativo &&
            atualizadoEm == other.atualizadoEm &&
            dtCadastroFacial == other.dtCadastroFacial &&
            dtCadastroConfirmado == other.dtCadastroConfirmado &&
            dtResetFacialAplicado == other.dtResetFacialAplicado &&
            dtRemocaoConfirmada == other.dtRemocaoConfirmada &&
            (embeddingFacial?.contentEquals(other.embeddingFacial ?: ByteArray(0)) ?: (other.embeddingFacial == null))
    }

    override fun hashCode(): Int {
        var result = matricula.hashCode()
        result = 31 * result + cpf.hashCode()
        result = 31 * result + nome.hashCode()
        result = 31 * result + ativo.hashCode()
        result = 31 * result + atualizadoEm.hashCode()
        result = 31 * result + (dtCadastroFacial?.hashCode() ?: 0)
        result = 31 * result + (dtCadastroConfirmado?.hashCode() ?: 0)
        result = 31 * result + (dtResetFacialAplicado?.hashCode() ?: 0)
        result = 31 * result + (dtRemocaoConfirmada?.hashCode() ?: 0)
        result = 31 * result + (embeddingFacial?.contentHashCode() ?: 0)
        return result
    }
}
