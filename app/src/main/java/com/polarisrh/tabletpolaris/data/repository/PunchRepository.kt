package com.polarisrh.tabletpolaris.data.repository

import kotlinx.coroutines.delay
import java.time.LocalDateTime

data class PunchResult(val matricula: String, val timestamp: LocalDateTime)

interface PunchRepository {
    suspend fun registerPunch(matricula: String): Result<PunchResult>
}

/**
 * Fake implementation used until the Polaris RH punch-clock API is defined.
 * Replace with a real HTTP-backed implementation later without touching call sites.
 */
class FakePunchRepository : PunchRepository {
    override suspend fun registerPunch(matricula: String): Result<PunchResult> {
        delay(800)
        if (matricula.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrícula inválida"))
        }
        return Result.success(PunchResult(matricula, LocalDateTime.now()))
    }
}
