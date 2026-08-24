package com.polarisrh.tabletpolaris.facial

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** Área do rosto em relação à área do PRÓPRIO OVAL (não do frame inteiro) — mais robusto a
 *  variações de resolução do bitmap capturado, e é diretamente o que o usuário vê na tela.
 *  Abaixo disso, o rosto está pequeno demais dentro do oval (longe da câmera). */
private const val RAZAO_MINIMA_AREA = 0.35f

/** Acima disso, o rosto está grande demais em relação ao oval (perto demais da câmera). */
private const val RAZAO_MAXIMA_AREA = 1.8f

/** Folga além do próprio oval antes de considerar o rosto "fora" — cobre tanto a diferença
 *  entre o retângulo do rosto e a curva oval (os cantos do oval não têm rosto mesmo estando
 *  bem posicionado) quanto o caso de um rosto BEM encaixado, encostando na borda do oval, que
 *  não pode ser rejeitado por estar "no limite" (por isso a margem é generosa, maior que só a
 *  diferença geométrica oval/retângulo exigiria). */
private const val MARGEM_TOLERANCIA_OVAL = 0.22f

/** Margem ao redor do bounding box do ML Kit (que é justo aos traços — olhos/nariz/boca, sem
 *  sobrar muita testa/queixo) antes de recortar pro embedding. Fixa essa proporção rosto/recorte
 *  SEMPRE igual, não importa a distância da câmera — sem isso, recortar pela área fixa do oval
 *  na tela fazia o "quanto do recorte é rosto de fato" variar conforme a pessoa se aproximava ou
 *  afastava, descalibrando a similaridade entre cadastro e reconhecimento (rosto perto: mais
 *  similar; rosto mais afastado: menos similar, mesma pessoa). */
private const val PADDING_RECORTE_ROSTO = 0.35f

/**
 * Roda a detecção facial direto no bitmap que está sendo exibido/capturado (PreviewView.bitmap),
 * comparando contra a área real do oval nesse MESMO bitmap. Não usa um stream de ImageAnalysis
 * separado da câmera — esse stream tinha campo de visão e resolução diferentes do que a pessoa
 * via no Preview, então "dentro do oval" e "dentro do que era analisado" eram coisas diferentes,
 * causando falsos positivos/negativos de enquadramento. Rodando na mesma imagem exibida, os dois
 * nunca mais divergem.
 */
class FacePositionChecker {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    suspend fun verificar(bitmap: Bitmap, ovalRect: Rect): FaceDetectionStatus {
        val faces = detectar(InputImage.fromBitmap(bitmap, 0))

        return when {
            faces.isEmpty() -> FaceDetectionStatus.SemRosto
            faces.size > 1 -> FaceDetectionStatus.MultiplosRostos
            else -> {
                val box = faces[0].boundingBox
                val areaRosto = box.width().toFloat() * box.height().toFloat()
                val areaOval = ovalRect.width().toFloat() * ovalRect.height().toFloat()
                val razaoArea = if (areaOval > 0f) areaRosto / areaOval else 0f

                val margemX = ovalRect.width() * MARGEM_TOLERANCIA_OVAL
                val margemY = ovalRect.height() * MARGEM_TOLERANCIA_OVAL
                val contido = box.left >= ovalRect.left - margemX &&
                    box.right <= ovalRect.right + margemX &&
                    box.top >= ovalRect.top - margemY &&
                    box.bottom <= ovalRect.bottom + margemY

                when {
                    razaoArea < RAZAO_MINIMA_AREA -> FaceDetectionStatus.RostoDistante
                    razaoArea > RAZAO_MAXIMA_AREA -> FaceDetectionStatus.RostoPerto
                    !contido -> FaceDetectionStatus.ForaDoCentro
                    else -> FaceDetectionStatus.Pronto
                }
            }
        }
    }

    /** Detecta e devolve o bounding box do rosto, só quando há exatamente um — usado pra
     *  recortar a amostra que vai pro embedding, sempre na mesma proporção rosto/recorte,
     *  independente de onde o rosto está na tela ou a que distância da câmera. */
    suspend fun detectarRostoUnico(bitmap: Bitmap): Rect? {
        val faces = detectar(InputImage.fromBitmap(bitmap, 0))
        return faces.singleOrNull()?.boundingBox
    }

    /** Recorta o bitmap ao redor do box do rosto, com uma margem fixa proporcional ao próprio
     *  box — não à área do oval ou do frame. */
    fun recortarRosto(bitmap: Bitmap, box: Rect): Bitmap {
        val paddingX = (box.width() * PADDING_RECORTE_ROSTO).toInt()
        val paddingY = (box.height() * PADDING_RECORTE_ROSTO).toInt()
        val left = (box.left - paddingX).coerceIn(0, bitmap.width - 1)
        val top = (box.top - paddingY).coerceIn(0, bitmap.height - 1)
        val right = (box.right + paddingX).coerceIn(left + 1, bitmap.width)
        val bottom = (box.bottom + paddingY).coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }

    private suspend fun detectar(inputImage: InputImage): List<Face> =
        suspendCancellableCoroutine { continuation ->
            detector.process(inputImage)
                .addOnSuccessListener { faces -> continuation.resume(faces) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
}
