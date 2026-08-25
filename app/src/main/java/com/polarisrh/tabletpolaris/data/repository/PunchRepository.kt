package com.polarisrh.tabletpolaris.data.repository

import java.time.LocalDateTime

data class PunchResult(val matricula: String, val timestamp: LocalDateTime)

interface PunchRepository {
    suspend fun registerPunch(matricula: String, nrScore: Float, nrThreshold: Float): Result<PunchResult>
}
