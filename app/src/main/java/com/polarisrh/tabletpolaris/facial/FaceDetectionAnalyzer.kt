package com.polarisrh.tabletpolaris.facial

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.abs

/** Área mínima do rosto em relação ao frame pra considerar "perto o suficiente" — mesma faixa
 *  usada hoje no cadastro do web (~0.05-0.07). */
private const val AREA_MINIMA_ROSTO = 0.06f

/** Quanto o centro do rosto pode se afastar do centro do frame (fração da largura/altura)
 *  antes de considerar "fora do centro" — sem isso, um rosto de canto passava a validação
 *  só por ter o tamanho certo. É uma aproximação (não é o mapeamento pixel-perfeito da
 *  posição do oval na tela), por isso a folga maior que o mínimo teórico. */
private const val TOLERANCIA_CENTRO = 0.25f

/** Quantos frames seguidos com o MESMO status novo até ele realmente trocar na tela — sem
 *  isso, uma medida bem na borda do limiar fica "piscando" entre dois estados a cada frame,
 *  porque a detecção sempre balança um pouco de frame pra frame. */
private const val FRAMES_PARA_TROCAR_STATUS = 4

/**
 * Roda a cada frame da câmera frontal. Não gera embedding nenhum — só classifica se o
 * enquadramento atual está bom o bastante pra permitir a captura (nenhum rosto, mais de um,
 * rosto pequeno/longe, fora do centro, ou pronto).
 */
class FaceDetectionAnalyzer(
    private val onResultado: (FaceDetectionStatus) -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .build()
    )

    private var statusEmitido: FaceDetectionStatus = FaceDetectionStatus.SemRosto
    private var statusCandidato: FaceDetectionStatus? = null
    private var contagemCandidato = 0

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val areaImagem = inputImage.width.toFloat() * inputImage.height.toFloat()

        detector.process(inputImage)
            .addOnSuccessListener { faces ->
                val statusBruto = when {
                    faces.isEmpty() -> FaceDetectionStatus.SemRosto
                    faces.size > 1 -> FaceDetectionStatus.MultiplosRostos
                    else -> {
                        val box = faces[0].boundingBox
                        val areaRosto = box.width().toFloat() * box.height().toFloat()

                        val deltaX = abs(box.exactCenterX() - inputImage.width / 2f) / inputImage.width
                        val deltaY = abs(box.exactCenterY() - inputImage.height / 2f) / inputImage.height

                        when {
                            areaImagem > 0f && areaRosto / areaImagem < AREA_MINIMA_ROSTO ->
                                FaceDetectionStatus.RostoDistante
                            deltaX > TOLERANCIA_CENTRO || deltaY > TOLERANCIA_CENTRO ->
                                FaceDetectionStatus.ForaDoCentro
                            else -> FaceDetectionStatus.Pronto
                        }
                    }
                }
                avaliarComDebounce(statusBruto)
            }
            .addOnFailureListener {
                avaliarComDebounce(FaceDetectionStatus.SemRosto)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun avaliarComDebounce(statusBruto: FaceDetectionStatus) {
        if (statusBruto == statusEmitido) {
            statusCandidato = null
            contagemCandidato = 0
            return
        }

        if (statusBruto == statusCandidato) {
            contagemCandidato++
        } else {
            statusCandidato = statusBruto
            contagemCandidato = 1
        }

        if (contagemCandidato >= FRAMES_PARA_TROCAR_STATUS) {
            statusEmitido = statusBruto
            statusCandidato = null
            contagemCandidato = 0
            onResultado(statusEmitido)
        }
    }
}
