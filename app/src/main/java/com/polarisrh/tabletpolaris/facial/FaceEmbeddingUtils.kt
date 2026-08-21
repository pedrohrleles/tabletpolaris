package com.polarisrh.tabletpolaris.facial

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Limiar de similaridade pra considerar "é a mesma pessoa". Ponto de partida — precisa de
 * calibração empírica com gente de verdade no tablet (ver conversa sobre o limiar 0.55 do
 * web, que usa outro modelo/métrica e não é diretamente comparável).
 */
const val LIMIAR_RECONHECIMENTO_FACIAL = 0.6f

/** Embeddings já vêm L2-normalizados do [FaceEmbeddingExtractor], então a similaridade de
 *  cosseno se reduz ao produto escalar simples. */
fun similaridadeCosseno(a: FloatArray, b: FloatArray): Float {
    var soma = 0f
    for (i in a.indices) soma += a[i] * b[i]
    return soma
}

fun FloatArray.paraByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())
    forEach { buffer.putFloat(it) }
    return buffer.array()
}

fun ByteArray.paraFloatArray(): FloatArray {
    val buffer = ByteBuffer.wrap(this).order(ByteOrder.nativeOrder())
    return FloatArray(size / Float.SIZE_BYTES) { buffer.float }
}
