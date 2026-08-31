package com.polarisrh.tabletpolaris.data.local

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Criptografa/descriptografa só o embedding facial (coluna embedding_tablet) antes de
 * gravar/ler do banco local — é o único dado do app que a LGPD trata como "dado sensível"
 * (biometria); o resto do banco (matrícula, CPF, nome, batidas) não passa por aqui.
 *
 * Chave simétrica (AES-256-GCM) presa ao Android Keystore, StrongBox-backed quando o hardware
 * suporta, nunca exportável — mesmo padrão do [DeviceKeyManager], só que com chave simétrica em
 * vez de par de chaves de assinatura.
 */
class EmbeddingCipher {

    fun encrypt(dados: ByteArray): ByteArray {
        ensureKeyExists()
        val cipher = Cipher.getInstance(TRANSFORMACAO).apply { init(Cipher.ENCRYPT_MODE, chave()) }
        // GCM exige um IV novo a cada operação — prefixa ele (tamanho fixo) no resultado, já
        // que precisa estar disponível de novo na hora de decriptar.
        return cipher.iv + cipher.doFinal(dados)
    }

    fun decrypt(dados: ByteArray): ByteArray {
        ensureKeyExists()
        val iv = dados.copyOfRange(0, TAMANHO_IV_BYTES)
        val textoCifrado = dados.copyOfRange(TAMANHO_IV_BYTES, dados.size)
        val cipher = Cipher.getInstance(TRANSFORMACAO).apply {
            init(Cipher.DECRYPT_MODE, chave(), GCMParameterSpec(TAMANHO_TAG_BITS, iv))
        }
        return cipher.doFinal(textoCifrado)
    }

    private fun chave(): SecretKey = keyStore().getKey(KEY_ALIAS, null) as SecretKey

    private fun ensureKeyExists() {
        if (keyStore().containsAlias(KEY_ALIAS)) return

        val supportsStrongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        if (supportsStrongBox) {
            try {
                gerarChave(useStrongBox = true)
                return
            } catch (e: StrongBoxUnavailableException) {
                // Tablet sem chip StrongBox — cai pro Keystore padrão abaixo.
            }
        }
        gerarChave(useStrongBox = false)
    }

    private fun gerarChave(useStrongBox: Boolean) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val specBuilder = KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (useStrongBox) {
            specBuilder.setIsStrongBoxBacked(true)
        }
        generator.init(specBuilder.build())
        generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "polaris_embedding_cipher_key"
        const val TRANSFORMACAO = "AES/GCM/NoPadding"
        const val TAMANHO_IV_BYTES = 12
        const val TAMANHO_TAG_BITS = 128
    }
}
