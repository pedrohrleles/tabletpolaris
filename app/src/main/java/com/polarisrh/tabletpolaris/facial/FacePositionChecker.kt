package com.polarisrh.tabletpolaris.facial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.hypot
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

/** Tamanho do recorte alinhado — já bate com o input esperado pelo MobileFaceNet (112x112),
 *  então o resize no [FaceEmbeddingExtractor] vira só uma garantia, não um redimensionamento
 *  de verdade. */
private const val TAMANHO_ALINHADO = 112

/** Posição-alvo dos olhos no recorte alinhado — template padrão de alinhamento 112x112 usado
 *  amplamente em pipelines de reconhecimento facial treinados com rostos alinhados por
 *  landmark (a mesma família de pré-processamento do MTCNN usada no treino do nosso
 *  MobileFaceNet). Fixando os olhos sempre nessa mesma posição/escala, o recorte final exclui
 *  de forma consistente a maior parte do cabelo/fundo — sem isso (recorte só pelo bounding box,
 *  sem rotação/escala normalizada), a proporção rosto/cabelo no recorte varia com a inclinação
 *  da cabeça e a distância da câmera, o que estava descalibrando a similaridade (inclusive
 *  entre a MESMA pessoa com penteados diferentes).
 */
private const val CENTRO_X_OLHOS_ALVO = 55.91f
private const val CENTRO_Y_OLHOS_ALVO = 51.6f
private const val DISTANCIA_OLHOS_ALVO = 35.24f

/**
 * Roda a detecção facial direto no bitmap que está sendo exibido/capturado (PreviewView.bitmap),
 * comparando contra a área real do oval nesse MESMO bitmap. Não usa um stream de ImageAnalysis
 * separado da câmera — esse stream tinha campo de visão e resolução diferentes do que a pessoa
 * via no Preview, então "dentro do oval" e "dentro do que era analisado" eram coisas diferentes,
 * causando falsos positivos/negativos de enquadramento. Rodando na mesma imagem exibida, os dois
 * nunca mais divergem.
 */
class FacePositionChecker {

    // Detector leve (sem landmarks), usado no polling frequente de enquadramento (a cada
    // 150ms) — só bounding box, que é tudo que [verificar] precisa.
    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    // Detector com landmarks (olhos), usado só nas poucas amostras reais que viram embedding.
    // FAST em vez de ACCURATE — os olhos são um ponto fácil de localizar (ao contrário de
    // contorno fino de rosto), então a perda de precisão é mínima, e o ganho de velocidade por
    // amostra ajuda a bater ponto mais rápido sem esperar cada detecção no modo mais lento.
    private val detectorComLandmarks = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .build()
    )

    suspend fun verificar(bitmap: Bitmap, ovalRect: Rect): FaceDetectionStatus {
        val faces = detectar(detector, bitmap)

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

    /**
     * Detecta o rosto (só quando há exatamente um) e devolve um recorte 112x112 ALINHADO pelos
     * olhos — rotacionado e escalado pra colocar os dois olhos sempre na mesma posição/distância
     * canônica, independente de como a cabeça está inclinada ou a que distância da câmera está.
     * Devolve null se não achar exatamente um rosto ou não conseguir os dois olhos (amostra é
     * descartada — como já colhemos várias por captura, perder uma não é problema).
     */
    suspend fun detectarEAlinhar(bitmap: Bitmap): Bitmap? {
        val face = detectar(detectorComLandmarks, bitmap).singleOrNull() ?: return null
        val olho1 = face.getLandmark(FaceLandmark.LEFT_EYE)?.position ?: return null
        val olho2 = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position ?: return null

        // Não confia no rótulo "esquerdo"/"direito" do ML Kit (é anatômico, do ponto de vista
        // da pessoa) — usa sempre o que está mais à esquerda NA IMAGEM, senão uma imagem
        // espelhada inverteria o sinal do ângulo calculado e giraria o recorte de cabeça pra
        // baixo.
        val olhoEsquerdoNaImagem = if (olho1.x <= olho2.x) olho1 else olho2
        val olhoDireitoNaImagem = if (olho1.x <= olho2.x) olho2 else olho1

        return alinhar(bitmap, olhoEsquerdoNaImagem, olhoDireitoNaImagem)
    }

    private fun alinhar(bitmap: Bitmap, olhoEsquerdo: PointF, olhoDireito: PointF): Bitmap {
        val dx = olhoDireito.x - olhoEsquerdo.x
        val dy = olhoDireito.y - olhoEsquerdo.y
        val anguloGraus = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
        val distanciaOlhos = hypot(dx, dy).coerceAtLeast(1e-3f)
        val escala = DISTANCIA_OLHOS_ALVO / distanciaOlhos

        val centroX = (olhoEsquerdo.x + olhoDireito.x) / 2f
        val centroY = (olhoEsquerdo.y + olhoDireito.y) / 2f

        val matrix = Matrix().apply {
            postTranslate(-centroX, -centroY)
            postRotate(-anguloGraus)
            postScale(escala, escala)
            postTranslate(CENTRO_X_OLHOS_ALVO, CENTRO_Y_OLHOS_ALVO)
        }

        val saida = Bitmap.createBitmap(TAMANHO_ALINHADO, TAMANHO_ALINHADO, Bitmap.Config.ARGB_8888)
        Canvas(saida).drawBitmap(bitmap, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return saida
    }

    private suspend fun detectar(cliente: com.google.mlkit.vision.face.FaceDetector, bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { continuation ->
            cliente.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { faces -> continuation.resume(faces) }
                .addOnFailureListener { continuation.resume(emptyList()) }
        }
}
