package com.polarisrh.tabletpolaris.facial

/** Espelha as validações de qualidade que o web já usa no cadastro (main.py/reconhecimento
 *  facial): nenhum rosto, mais de um rosto, ou rosto pequeno/longe demais são rejeitados. */
sealed interface FaceDetectionStatus {
    data object SemRosto : FaceDetectionStatus
    data object MultiplosRostos : FaceDetectionStatus
    data object RostoDistante : FaceDetectionStatus
    /** Rosto grande demais em relação ao oval — câmera perto demais do rosto. */
    data object RostoPerto : FaceDetectionStatus
    /** Rosto do tamanho certo, mas fora do centro do enquadramento (ex.: canto da câmera). */
    data object ForaDoCentro : FaceDetectionStatus
    data object Pronto : FaceDetectionStatus
}
