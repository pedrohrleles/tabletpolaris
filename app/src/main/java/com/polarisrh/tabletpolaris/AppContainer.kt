package com.polarisrh.tabletpolaris

import android.content.Context
import androidx.room.Room
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.BatidaPendenteDao
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.PolarisDatabase
import com.polarisrh.tabletpolaris.data.remote.PolarisApiClient
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.repository.ColaboradorSyncRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceRevocationHandler
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.FakePunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.RemoteDeviceAuthRepository
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {

    val credentialsStore: DeviceCredentialsStore = DeviceCredentialsStore(context)

    val polarisApiService: PolarisApiService = PolarisApiClient.service

    val punchRepository: PunchRepository = FakePunchRepository()

    /** MobileFaceNet (.tflite) — gera embeddings faciais 100% on-device, sem rede. */
    val faceEmbeddingExtractor: FaceEmbeddingExtractor = FaceEmbeddingExtractor(context)

    /**
     * Sinal compartilhado: quando o backend informa (via heartbeat ou via /status) que este
     * coletor foi desativado remotamente, guarda a mensagem aqui. A UI observa e reage — limpa
     * as credenciais e volta pra tela de ativação, mesmo com o app já aberto em outra tela.
     */
    val deviceRevocationMessage: MutableStateFlow<String?> = MutableStateFlow(null)

    val networkMonitor: NetworkMonitor = NetworkMonitor(context)

    /**
     * Banco local (SQLite via Room) — roster de colaboradores (matrícula/cpf/nome/embedding
     * facial) e fila de batidas offline. Nunca sincroniza com o Polaris RH além do roster em
     * si; o embedding facial é gerado e lido só neste tablet.
     */
    private val database: PolarisDatabase = Room.databaseBuilder(
        context.applicationContext,
        PolarisDatabase::class.java,
        "polaris.db"
    ).build()

    val colaboradorDao: ColaboradorDao = database.colaboradorDao()

    val batidaPendenteDao: BatidaPendenteDao = database.batidaPendenteDao()

    /**
     * Único ponto que "desvincula" o tablet: limpa a sessão/credenciais. Não mexe no cache de
     * colaboradores nem na fila de batidas — ver [DeviceRevocationHandler].
     */
    val deviceRevocationHandler: DeviceRevocationHandler = DeviceRevocationHandler(
        credentialsStore = credentialsStore,
        onRevoked = { message -> deviceRevocationMessage.value = message }
    )

    val colaboradorSyncRepository: ColaboradorSyncRepository = ColaboradorSyncRepository(
        api = polarisApiService,
        credentialsStore = credentialsStore,
        colaboradorDao = colaboradorDao
    )

    val deviceStatusChecker: DeviceStatusChecker = DeviceStatusChecker(
        api = polarisApiService,
        credentialsStore = credentialsStore,
        colaboradorSyncRepository = colaboradorSyncRepository,
        revocationHandler = deviceRevocationHandler
    )

    val deviceAuthRepository: DeviceAuthRepository =
        RemoteDeviceAuthRepository(context, polarisApiService, credentialsStore, colaboradorSyncRepository)
}
