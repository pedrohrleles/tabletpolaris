package com.polarisrh.tabletpolaris.facial

/** Só valida presença de rosto: nenhum, mais de um, ou exatamente um (Pronto). Sem checagem de
 *  tamanho/posição — a etapa de alinhamento (ver [FacePositionChecker.detectarEAlinhar]) já
 *  normaliza o recorte final pro tamanho que o MobileFaceNet espera, independente de distância;
 *  validar tamanho aqui (com zoom ou área mínima) só forçava um "encaixe" artificial que
 *  piorava a nitidez do recorte real, sem ajudar em nada — medido em campo (similaridade caindo
 *  pra perto do limiar com zoom aplicado). */
sealed interface FaceDetectionStatus {
    data object SemRosto : FaceDetectionStatus
    data object MultiplosRostos : FaceDetectionStatus
    data object Pronto : FaceDetectionStatus
}
