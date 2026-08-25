package com.polarisrh.tabletpolaris.data.local

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Owns this tablet's asymmetric identity key, generated and kept inside the Android
 * Keystore (StrongBox-backed when the hardware supports it). The private key never
 * leaves the keystore — only the public key is ever sent to the Polaris RH backend,
 * at device-activation time, matching the pairing model already modeled in
 * rep_core_coletor_dispositivo (cd_chave_publica / fl_strongbox).
 */
class DeviceKeyManager {

    fun getOrCreatePublicKeyBase64(): String {
        ensureKeyExists()
        val certificate = keyStore().getCertificate(KEY_ALIAS)
        return Base64.encodeToString(certificate.publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Assina [payload] com a chave privada do dispositivo (nunca sai do Keystore) usando
     * SHA256withECDSA — mesmo algoritmo que o backend usa pra verificar contra a chave pública
     * pareada na ativação. Devolve a assinatura DER, em base64 (sem quebra de linha).
     *
     * Formato esperado do payload pra assinar uma marcação (confirmado com o backend):
     * `{id_coletor}|{id_local}|{nr_matricula}|{dt_hr_marcacao}` — quatro valores, join com "|",
     * sem espaços, sem terminador. Montar essa string é responsabilidade de quem chama; aqui só
     * assina o que for passado.
     */
    fun assinar(payload: String): String {
        ensureKeyExists()
        val privateKey = keyStore().getKey(KEY_ALIAS, null) as PrivateKey
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(payload.toByteArray(Charsets.UTF_8))
        }
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    fun isStrongBoxBacked(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return try {
            val privateKey = keyStore().getKey(KEY_ALIAS, null) as PrivateKey
            val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            keyInfo.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureKeyExists() {
        if (keyStore().containsAlias(KEY_ALIAS)) return

        val supportsStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        if (supportsStrongBox) {
            try {
                generateKeyPair(useStrongBox = true)
                return
            } catch (e: StrongBoxUnavailableException) {
                // This tablet's hardware has no StrongBox chip — fall back below.
            }
        }
        generateKeyPair(useStrongBox = false)
    }

    private fun generateKeyPair(useStrongBox: Boolean) {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        if (useStrongBox) {
            specBuilder.setIsStrongBoxBacked(true)
        }
        generator.initialize(specBuilder.build())
        generator.generateKeyPair()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "polaris_device_identity_key"
    }
}
