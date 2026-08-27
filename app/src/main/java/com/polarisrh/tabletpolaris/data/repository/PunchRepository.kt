package com.polarisrh.tabletpolaris.data.repository

import java.time.Instant

/** [timestamp] é o mesmo instante do relógio do tablet usado no dt_hr_marcacao assinado —
 *  usado só pra exibição (tela de sucesso). */
data class PunchResult(val matricula: String, val timestamp: Instant)

interface PunchRepository {
    suspend fun registerPunch(matricula: String, nrScore: Float, nrThreshold: Float): Result<PunchResult>
}
