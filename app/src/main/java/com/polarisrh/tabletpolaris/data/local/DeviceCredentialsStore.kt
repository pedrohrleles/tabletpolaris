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
     * Limpa só a sessão/credenciais (token, ids, etc). Propositalmente NÃO mexe nas chaves de
     * cache de colaboradores ([idEstabelecimentoColaboradoresCache]/[ultimaSincronizacaoColaboradores])
     * — a decisão de preservar ou zerar esse cache só é tomada na próxima ativação, quando dá
     * pra comparar o estabelecimento novo com o que estava salvo. Ver [salvarIdEstabelecimentoColaboradoresCache].
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
            .apply()
    }

    /** dt_sincronizacao da última carga (completa ou incremental) de colaboradores aplicada
     *  com sucesso — comparado contra dt_cadastro_alterado do /status pra saber se precisa
     *  sincronizar de novo. */
    fun ultimaSincronizacaoColaboradores(): String? = prefs.getString(KEY_ULTIMA_SYNC_COLABORADORES, null)

    fun salvarUltimaSincronizacaoColaboradores(dtSincronizacao: String) {
        prefs.edit().putString(KEY_ULTIMA_SYNC_COLABORADORES, dtSincronizacao).apply()
    }

    /** A qual estabelecimento (local de trabalho) pertence o cache local de colaboradores
     *  (embeddings inclusive) atualmente salvo — usado na próxima ativação pra decidir se
     *  preserva ou zera o cache. Uma empresa pode ter vários estabelecimentos: o roster de
     *  colaboradores é filtrado por estabelecimento no backend, então é nesse nível que a
     *  comparação precisa acontecer, não no nível de empresa. */
    fun idEstabelecimentoColaboradoresCache(): String? = prefs.getString(KEY_ID_ESTABELECIMENTO_COLABORADORES_CACHE, null)

    fun salvarIdEstabelecimentoColaboradoresCache(idEstabelecimento: String) {
        prefs.edit().putString(KEY_ID_ESTABELECIMENTO_COLABORADORES_CACHE, idEstabelecimento).apply()
    }

    fun limparCacheColaboradores() {
        prefs.edit()
            .remove(KEY_ID_ESTABELECIMENTO_COLABORADORES_CACHE)
            .remove(KEY_ULTIMA_SYNC_COLABORADORES)
            .apply()
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
        const val KEY_ULTIMA_SYNC_COLABORADORES = "ultima_sincronizacao_colaboradores"
        const val KEY_ID_ESTABELECIMENTO_COLABORADORES_CACHE = "id_estabelecimento_colaboradores_cache"
    }
}
