package com.polarisrh.tabletpolaris.data.repository

import com.polarisrh.tabletpolaris.data.local.DeviceCredentials
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import kotlinx.coroutines.delay

interface DeviceAuthRepository {
    suspend fun activateDevice(activationCode: String): Result<Unit>
    fun hasStoredCredentials(): Boolean
}

/**
 * Fake implementation used until the Polaris RH device-activation API is defined.
 * Replace with a real HTTP-backed implementation later without touching call sites.
 */
class FakeDeviceAuthRepository(
    private val credentialsStore: DeviceCredentialsStore
) : DeviceAuthRepository {

    override suspend fun activateDevice(activationCode: String): Result<Unit> {
        delay(600)
        if (activationCode.isBlank()) {
            return Result.failure(IllegalArgumentException("Informe o código de ativação"))
        }
        credentialsStore.save(DeviceCredentials(activationCode))
        return Result.success(Unit)
    }

    override fun hasStoredCredentials(): Boolean = credentialsStore.read() != null
}
