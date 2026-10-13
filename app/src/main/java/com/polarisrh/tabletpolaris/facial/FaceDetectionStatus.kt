package com.polarisrh.tabletpolaris.facial

/** Presença de rosto (nenhum, mais de um, ou exatamente um) e, só quando há exatamente um, a
 *  pose da cabeça e o quanto ele preenche o quadro (ver [FacePositionChecker]). SEM checagem de
 *  tamanho MÍNIMO — a etapa de alinhamento (ver [FacePositionChecker.detectarEAlinhar]) já
 *  normaliza o recorte final pro tamanho que o MobileFaceNet espera, independente de distância;
 *  validar um mínimo aqui (com zoom ou área mínima) só forçava um "encaixe" artificial que
 *  piorava a nitidez do recorte real, sem ajudar em nada — medido em campo (similaridade caindo
 *  pra perto do limiar com zoom aplicado). O MÁXIMO é outra história — ver [RostoMuitoProximo]. */
sealed interface FaceDetectionStatus {
    data object SemRosto : FaceDetectionStatus
    data object MultiplosRostos : FaceDetectionStatus

    /** Rosto grande demais no quadro (câmera muito perto) — distorce proporções (efeito de lente
     *  grande angular, tipo selfie colada) e pode cortar parte do rosto fora da imagem. Isso SIM
     *  é checagem de tamanho, mas só o limite MÁXIMO — o problema do limite mínimo (forçar
     *  "encaixe" com zoom) nunca existiu aqui: é só rejeitar e pedir pra afastar um pouco. */
    data object RostoMuitoProximo : FaceDetectionStatus

    /** Rosto virado de lado (yaw) além do aceitável — câmera vendo o rosto de perfil/três
     *  quartos demais pra gerar um embedding confiável. */
    data object RostoVirado : FaceDetectionStatus

    /** Cabeça inclinada pra cima/baixo (pitch) além do aceitável. Tombar a cabeça pro OMBRO
     *  (roll, giro no próprio plano da imagem) não entra aqui de propósito — isso já é corrigido
     *  matematicamente pelo alinhamento por olhos em [FacePositionChecker.detectarEAlinhar],
     *  então bloquear por causa disso só atrapalharia sem necessidade. */
    data object RostoInclinado : FaceDetectionStatus

    data object Pronto : FaceDetectionStatus
}
