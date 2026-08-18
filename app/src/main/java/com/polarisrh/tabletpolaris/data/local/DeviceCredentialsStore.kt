package com.polarisrh.tabletpolaris.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class DeviceCredentials(val activationCode: String)

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
            .putString(KEY_ACTIVATION_CODE, credentials.activationCode)
            .apply()
    }

    fun read(): DeviceCredentials? {
        val code = prefs.getString(KEY_ACTIVATION_CODE, null) ?: return null
        return DeviceCredentials(code)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "device_credentials"
        const val KEY_ACTIVATION_CODE = "activation_code"
    }
}
