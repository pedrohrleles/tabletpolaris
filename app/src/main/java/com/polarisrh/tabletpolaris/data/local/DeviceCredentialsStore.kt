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

    fun clear() {
        prefs.edit().clear().apply()
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
    }
}
