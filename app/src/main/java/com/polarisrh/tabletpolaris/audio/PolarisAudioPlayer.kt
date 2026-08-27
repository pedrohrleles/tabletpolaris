package com.polarisrh.tabletpolaris.audio

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import com.polarisrh.tabletpolaris.R
import java.io.IOException

/**
 * Toca os áudios gravados de confirmação ("Facial cadastrada com sucesso!", "Ponto
 * registrado!") — arquivos bundlados no próprio APK ([R.raw.facial_cadastrada],
 * [R.raw.ponto_registrado]), então funciona 100% offline, sem depender de nenhum motor de TTS
 * (nem da voz instalada) no aparelho.
 */
class PolarisAudioPlayer(private val context: Context) {

    // Precisa ser um campo da classe (não uma variável local dentro de [tocar]) — sem uma
    // referência forte do lado Kotlin segurando o MediaPlayer durante a reprodução, o garbage
    // collector pode coletá-lo NO MEIO do áudio, cortando o final.
    private var player: MediaPlayer? = null

    fun tocarFacialCadastrada() = tocar(R.raw.facial_cadastrada)

    fun tocarPontoRegistrado() = tocar(R.raw.ponto_registrado)

    /**
     * [MediaPlayer.create] faz o `prepare()` de forma SÍNCRONA — como isso é chamado de dentro
     * de um `LaunchedEffect` (thread principal/UI do Compose), travava a tela por um instante
     * enquanto decodificava o áudio. Aqui monta o player manualmente e usa `prepareAsync()`,
     * que devolve o controle na hora e só chama [MediaPlayer.start] quando a decodificação
     * termina (via callback) — a tela nunca fica esperando a decodificação do áudio.
     */
    private fun tocar(resId: Int) {
        player?.release()
        val novoPlayer = MediaPlayer()
        player = novoPlayer

        try {
            context.resources.openRawResourceFd(resId).use { descritor ->
                novoPlayer.setDataSource(descritor.fileDescriptor, descritor.startOffset, descritor.length)
            }
        } catch (e: IOException) {
            Log.w(TAG, "Falha ao abrir áudio (resId=$resId): ${e.message}")
            novoPlayer.release()
            player = null
            return
        }

        novoPlayer.setOnPreparedListener { it.start() }
        novoPlayer.setOnCompletionListener { finalizado ->
            finalizado.release()
            if (player === finalizado) player = null
        }
        novoPlayer.setOnErrorListener { comErro, _, _ ->
            comErro.release()
            if (player === comErro) player = null
            true
        }
        novoPlayer.prepareAsync()
    }

    private companion object {
        const val TAG = "PolarisAudioPlayer"
    }
}
