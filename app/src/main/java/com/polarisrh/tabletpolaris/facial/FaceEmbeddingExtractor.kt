package com.polarisrh.tabletpolaris.facial

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max
import kotlin.math.sqrt

private const val MODEL_FILE = "mobilefacenet.tflite"
private const val INPUT_SIZE = 112
private const val EMBEDDING_SIZE = 192
private const val IMAGE_MEAN = 127.5f
private const val IMAGE_STD = 128f

/** O grafo desse .tflite foi exportado com o lote fixo em 2 (o app original sempre comparava
 *  duas fotos de uma vez) — rodar com lote 1 faz o delegate XNNPack falhar ao realocar os
 *  tensores (IllegalStateException: "failed to reshape runtime"). Contornamos duplicando a
 *  mesma imagem nas duas posições do lote e usando só o primeiro embedding da saída. */
private const val TAMANHO_LOTE = 2

/**
 * Gera o embedding facial (vetor de 192 dimensões, L2-normalizado) a partir de um bitmap com
 * o rosto. Modelo: MobileFaceNet (assets/mobilefacenet.tflite — conversão de código aberto,
 * licença MIT, do sirius-ai/MobileFaceNet_TF via syaringan357/Android-MobileFaceNet-*).
 * Roda inteiramente on-device, sem rede nenhuma.
 */
class FaceEmbeddingExtractor(context: Context) {

    private val interpreter = Interpreter(
        loadModelFile(context),
        Interpreter.Options().apply { setNumThreads(4) }
    )

    fun extrair(bitmap: Bitmap): FloatArray {
        val redimensionado = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val imagemProcessada = construirEntrada(redimensionado)
        val entrada = Array(TAMANHO_LOTE) { imagemProcessada }
        val saida = Array(TAMANHO_LOTE) { FloatArray(EMBEDDING_SIZE) }
        interpreter.run(entrada, saida)
        return l2Normalizar(saida[0])
    }

    private fun construirEntrada(bitmap: Bitmap): Array<Array<FloatArray>> {
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        return Array(INPUT_SIZE) { y ->
            Array(INPUT_SIZE) { x ->
                val pixel = pixels[y * INPUT_SIZE + x]
                floatArrayOf(
                    (((pixel shr 16) and 0xFF) - IMAGE_MEAN) / IMAGE_STD,
                    (((pixel shr 8) and 0xFF) - IMAGE_MEAN) / IMAGE_STD,
                    ((pixel and 0xFF) - IMAGE_MEAN) / IMAGE_STD
                )
            }
        }
    }

    private fun l2Normalizar(embedding: FloatArray, epsilon: Double = 1e-10): FloatArray {
        val somaQuadrados = embedding.sumOf { it.toDouble() * it }
        val norma = sqrt(max(somaQuadrados, epsilon)).toFloat()
        return FloatArray(embedding.size) { i -> embedding[i] / norma }
    }

    private fun loadModelFile(context: Context): MappedByteBuffer {
        val descritor = context.assets.openFd(MODEL_FILE)
        val inputStream = FileInputStream(descritor.fileDescriptor)
        return inputStream.channel.map(
            FileChannel.MapMode.READ_ONLY,
            descritor.startOffset,
            descritor.declaredLength
        )
    }
}
