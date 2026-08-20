package com.polarisrh.tabletpolaris

import android.content.Context
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.remote.PolarisApiClient
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.FakePunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.RemoteDeviceAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {

    val credentialsStore: DeviceCredentialsStore = DeviceCredentialsStore(context)

    val polarisApiService: PolarisApiService = PolarisApiClient.service

    val deviceAuthRepository: DeviceAuthRepository =
        RemoteDeviceAuthRepository(context, polarisApiService, credentialsStore)

    val punchRepository: PunchRepository = FakePunchRepository()

    /**
     * Sinal compartilhado: quando o backend informa (via heartbeat ou via /status) que este
     * coletor foi desativado remotamente, guarda a mensagem aqui. A UI observa e reage — limpa
     * as credenciais e volta pra tela de ativação, mesmo com o app já aberto em outra tela.
     */
    val deviceRevocationMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val networkMonitor: NetworkMonitor = NetworkMonitor(context)

    val deviceStatusChecker: DeviceStatusChecker = DeviceStatusChecker(
        api = polarisApiService,
        credentialsStore = credentialsStore,
        onRevoked = { message -> deviceRevocationMessage.value = message }
    )
}
