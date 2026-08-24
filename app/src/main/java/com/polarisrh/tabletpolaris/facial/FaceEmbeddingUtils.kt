package com.polarisrh.tabletpolaris.facial

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Limiar de similaridade pra considerar "é a mesma pessoa". Calibração empírica em andamento
 * (ver tabela de debug `tentativa_reconhecimento`, que loga a similaridade calculada em toda
 * tentativa — não é diretamente comparável ao limiar 0.55 do web, que usa outro modelo/métrica).
 * Subido de 0.6 pra 0.75 depois de um falso positivo real (colaborador diferente reconhecido
 * com similaridade 0.65, cadastro feito já na versão atual do recorte por rosto detectado —
 * não era resíduo de pipeline antigo). 0.75 se mostrou rígido demais no uso real (rejeitando
 * gente de verdade), então baixado pra 0.70 — ainda com folga acima do falso positivo
 * conhecido (0.65) e abaixo do match genuíno observado (0.811), mas mais permissivo que 0.75.
 * Com poucos pontos de dado de cada lado, continua precisando de mais tentativas reais
 * (positivas e negativas, ver tabela `tentativa_reconhecimento`) pra confirmar esse valor.
 */
const val LIMIAR_RECONHECIMENTO_FACIAL = 0.70f

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
