package com.polarisrh.tabletpolaris.facial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlinx.coroutines.suspendCancellableCoroutine

/** Quanto o rosto pode estar virado (yaw) ou com a cabeça abaixada/levantada (pitch) antes de
 *  rejeitar o enquadramento como "não pronto". ML Kit calcula os dois por padrão (não precisa de
 *  LANDMARK_MODE nem CLASSIFICATION_MODE), com sinal: Y negativo = virado pra esquerda de quem
 *  olha a câmera, X negativo = cabeça abaixada. 15° é o valor inicial — frontal o bastante pra
 *  não degradar a similaridade do MobileFaceNet, mas sem exigir uma pose perfeita impossível de
 *  segurar parado por 2-3s; ajustar aqui depois de medir em campo, igual já foi feito com
 *  [com.polarisrh.tabletpolaris.facial.LIMIAR_RECONHECIMENTO_FACIAL]. */
private const val LIMITE_YAW_GRAUS = 15f
private const val LIMITE_PITCH_GRAUS = 15f

/** Fração máxima da altura do quadro que o rosto pode ocupar antes de rejeitar como "muito
 *  perto" — rosto colado na câmera distorce proporções (efeito de lente grande angular) e pode
 *  cortar queixo/testa fora da imagem, bem diferente da distância normal de uso. Medido em campo
 *  (log): o próprio ML Kit já para de detectar rosto nenhum por volta de 0.75-0.76 de proporção
 *  — bem antes do primeiro chute de 0.85, que nunca chegava a disparar. 0.65 dá uma margem real
 *  de aviso ("afaste um pouco") antes dessa zona onde a detecção já falha de vez. */
private const val LIMITE_PROPORCAO_ROSTO_NO_QUADRO = 0.65f

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
 * Roda a detecção facial direto no bitmap que está sendo exibido/capturado (PreviewView.bitmap).
 * Não usa um stream de ImageAnalysis separado da câmera — esse stream tinha campo de visão e
 * resolução diferentes do que a pessoa via no Preview, então o que era validado e o que era
 * exibido eram coisas diferentes, causando falsos positivos/negativos. Rodando na mesma imagem
 * exibida, os dois nunca mais divergem.
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

    /** Valida presença de rosto (nenhum/um/mais de um) e, havendo exatamente um, proximidade e
     *  pose da cabeça — ver justificativa de não checar um MÍNIMO de tamanho em
     *  [FaceDetectionStatus]. */
    suspend fun verificar(bitmap: Bitmap): FaceDetectionStatus {
        val faces = detectar(detector, bitmap)

        return when {
            faces.isEmpty() -> FaceDetectionStatus.SemRosto
            faces.size > 1 -> FaceDetectionStatus.MultiplosRostos
            else -> avaliarRosto(faces.single(), bitmap)
        }
    }

    /** PERFORMANCE_MODE_FAST (sem landmark/classification) ainda calcula ângulos de pose e
     *  bounding box — fazem parte da detecção base do ML Kit, não são um modo extra a ativar.
     *  Proximidade checada primeiro: rosto colado na câmera costuma distorcer os ângulos de
     *  pose também, então o aviso de "afaste um pouco" é mais direto ao ponto que mandar
     *  endireitar uma pose que já está errada pelo enquadramento, não pela cabeça em si. */
    private fun avaliarRosto(face: Face, bitmap: Bitmap): FaceDetectionStatus {
        val proporcaoAltura = face.boundingBox.height().toFloat() / bitmap.height
        return when {
            proporcaoAltura > LIMITE_PROPORCAO_ROSTO_NO_QUADRO -> FaceDetectionStatus.RostoMuitoProximo
            abs(face.headEulerAngleY) > LIMITE_YAW_GRAUS -> FaceDetectionStatus.RostoVirado
            abs(face.headEulerAngleX) > LIMITE_PITCH_GRAUS -> FaceDetectionStatus.RostoInclinado
            else -> FaceDetectionStatus.Pronto
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
