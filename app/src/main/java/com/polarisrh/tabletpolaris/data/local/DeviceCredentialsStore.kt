package com.polarisrh.tabletpolaris.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class DeviceCredentials(
    val token: String,
    val idColetor: String,
    val idColetorLogico: String,
    val idEmpregador: String,
    val idEstabelecimento: String?,
    val nomeEmpregador: String,
    val timezone: String
)

class DeviceCredentialsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(credentials: DeviceCredentials) {
        prefs.edit()
            .putString(KEY_TOKEN, credentials.token)
            .putString(KEY_ID_COLETOR, credentials.idColetor)
            .putString(KEY_ID_COLETOR_LOGICO, credentials.idColetorLogico)
            .putString(KEY_ID_EMPREGADOR, credentials.idEmpregador)
            .putString(KEY_ID_ESTABELECIMENTO, credentials.idEstabelecimento)
            .putString(KEY_NOME_EMPREGADOR, credentials.nomeEmpregador)
            .putString(KEY_TIMEZONE, credentials.timezone)
            .apply()
    }

    fun read(): DeviceCredentials? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val idColetor = prefs.getString(KEY_ID_COLETOR, null) ?: return null
        val idColetorLogico = prefs.getString(KEY_ID_COLETOR_LOGICO, null) ?: return null
        val idEmpregador = prefs.getString(KEY_ID_EMPREGADOR, null) ?: return null
        val nomeEmpregador = prefs.getString(KEY_NOME_EMPREGADOR, null) ?: return null
        val timezone = prefs.getString(KEY_TIMEZONE, null) ?: return null
        return DeviceCredentials(
            token = token,
            idColetor = idColetor,
            idColetorLogico = idColetorLogico,
            idEmpregador = idEmpregador,
            idEstabelecimento = prefs.getString(KEY_ID_ESTABELECIMENTO, null),
            nomeEmpregador = nomeEmpregador,
            timezone = timezone
        )
    }

    /**
     * Limpa só a sessão/credenciais (token, ids, etc). Propositalmente NÃO mexe na chave de
     * cache de colaboradores ([idEmpregadorColaboradoresCache]) — a decisão de preservar ou
     * zerar esse cache só é tomada na próxima ativação, quando dá pra comparar a empresa nova
     * com a que estava salva. Ver [salvarIdEmpregadorColaboradoresCache].
     */
    fun clear() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_ID_COLETOR)
            .remove(KEY_ID_COLETOR_LOGICO)
            .remove(KEY_ID_EMPREGADOR)
            .remove(KEY_ID_ESTABELECIMENTO)
            .remove(KEY_NOME_EMPREGADOR)
            .remove(KEY_TIMEZONE)
            .remove(KEY_BLOQUEADO_DESATIVACAO)
            .apply()
    }

    /** Ver [com.polarisrh.tabletpolaris.data.repository.DesativacaoHandler] — persistido (não
     *  só em memória) porque o processo de esvaziar a fila e confirmar com o servidor pode
     *  atravessar reboots. Capturado no INSTANTE em que a desativação é vista pela primeira
     *  vez, nunca desfeito sozinho (só via [clear] ou [limparBloqueioPorDesativacao]). */
    fun estaBloqueadoPorDesativacao(): Boolean = prefs.getBoolean(KEY_BLOQUEADO_DESATIVACAO, false)

    fun marcarBloqueadoPorDesativacao() {
        prefs.edit().putBoolean(KEY_BLOQUEADO_DESATIVACAO, true).apply()
    }

    /** Chamada isolada (fora do [clear] geral) numa ativação nova bem-sucedida — ver
     *  [com.polarisrh.tabletpolaris.data.repository.DesativacaoHandler.resetar]. */
    fun limparBloqueioPorDesativacao() {
        prefs.edit().remove(KEY_BLOQUEADO_DESATIVACAO).apply()
    }

    /** A qual empresa pertence o cache local de colaboradores (embeddings inclusive)
     *  atualmente salvo — usado na próxima ativação pra decidir se preserva ou zera o cache.
     *  O roster de colaboradores agora é da empresa inteira (todos os estabelecimentos, ex.:
     *  um colaborador de Viçosa que substitui alguém em Muriaé já aparece no roster de lá,
     *  só sem embedding facial até cadastrar naquele tablet), então a comparação é por
     *  empresa, não por estabelecimento. */
    fun idEmpregadorColaboradoresCache(): String? = prefs.getString(KEY_ID_EMPREGADOR_COLABORADORES_CACHE, null)

    fun salvarIdEmpregadorColaboradoresCache(idEmpregador: String) {
        prefs.edit().putString(KEY_ID_EMPREGADOR_COLABORADORES_CACHE, idEmpregador).apply()
    }

    fun limparCacheColaboradores() {
        prefs.edit()
            .remove(KEY_ID_EMPREGADOR_COLABORADORES_CACHE)
            .apply()
    }

    /** Último nr_sequencia_lote efetivamente usado num envio de marcações — persistido pra
     *  garantir que o próximo valor seja sempre maior mesmo se o relógio do tablet regredir
     *  (correção NTP, troca de fuso, RTC descarregado). Sem isso, "gerar outro timestamp" no
     *  409 pode nunca produzir um valor maior que o último aceito, travando a sincronização. */
    fun ultimaSequenciaLote(): Long? = prefs.getLong(KEY_ULTIMA_SEQUENCIA_LOTE, -1L).takeIf { it >= 0L }

    fun salvarUltimaSequenciaLote(valor: Long) {
        prefs.edit().putLong(KEY_ULTIMA_SEQUENCIA_LOTE, valor).apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "device_credentials"
        const val KEY_TOKEN = "token"
        const val KEY_ID_COLETOR = "id_coletor"
        const val KEY_ID_COLETOR_LOGICO = "id_coletor_logico"
        const val KEY_ID_EMPREGADOR = "id_empregador"
        const val KEY_ID_ESTABELECIMENTO = "id_estabelecimento"
        const val KEY_NOME_EMPREGADOR = "nome_empregador"
        const val KEY_TIMEZONE = "timezone"
        const val KEY_ID_EMPREGADOR_COLABORADORES_CACHE = "id_empregador_colaboradores_cache"
        const val KEY_ULTIMA_SEQUENCIA_LOTE = "ultima_sequencia_lote"
        const val KEY_BLOQUEADO_DESATIVACAO = "bloqueado_desativacao"
    }
}
