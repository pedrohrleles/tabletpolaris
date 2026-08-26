package com.polarisrh.tabletpolaris

import android.content.Context
import androidx.room.Room
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_1_2
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_2_3
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_3_4
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_4_5
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_5_6
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_6_7
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_7_8
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_8_9
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_9_10
import com.polarisrh.tabletpolaris.data.local.db.MIGRATION_10_11
import com.polarisrh.tabletpolaris.data.local.db.PolarisDatabase
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoDao
import com.polarisrh.tabletpolaris.data.remote.PolarisApiClient
import com.polarisrh.tabletpolaris.data.remote.PolarisApiService
import com.polarisrh.tabletpolaris.data.repository.ColaboradorSyncRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceRevocationHandler
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchSyncRepository
import com.polarisrh.tabletpolaris.data.repository.RemoteDeviceAuthRepository
import com.polarisrh.tabletpolaris.data.repository.RoomPunchRepository
import com.polarisrh.tabletpolaris.audio.PolarisAudioPlayer
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
import kotlinx.coroutines.flow.MutableStateFlow

class AppContainer(context: Context) {

    val credentialsStore: DeviceCredentialsStore = DeviceCredentialsStore(context)

    val polarisApiService: PolarisApiService = PolarisApiClient.service

    /** MobileFaceNet (.tflite) — gera embeddings faciais 100% on-device, sem rede. */
    val faceEmbeddingExtractor: FaceEmbeddingExtractor = FaceEmbeddingExtractor(context)

    /** Toca os áudios gravados de confirmação de sucesso (facial cadastrada, ponto
     *  registrado) — arquivos bundlados no APK, 100% offline. */
    val audioPlayer: PolarisAudioPlayer = PolarisAudioPlayer(context)

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
    )
        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
        .build()

    val colaboradorDao: ColaboradorDao = database.colaboradorDao()

    val batidaDao: BatidaDao = database.batidaDao()

    /** Auditoria de tentativas de reconhecimento (espelha rep_aud_biometria_log do web) —
     *  ajuda a calibrar o limiar com dados reais em vez de chutar. */
    val tentativaReconhecimentoDao: TentativaReconhecimentoDao = database.tentativaReconhecimentoDao()

    /** Grava local (fila offline) na hora e nunca espera rede — ver RoomPunchRepository. */
    val punchRepository: PunchRepository = RoomPunchRepository(
        context = context,
        batidaDao = batidaDao,
        colaboradorDao = colaboradorDao,
        credentialsStore = credentialsStore
    )

    /** Drena a fila offline de batidas contra o backend — chamado só pelo PunchSyncWorker. */
    val punchSyncRepository: PunchSyncRepository = PunchSyncRepository(
        api = polarisApiService,
        credentialsStore = credentialsStore,
        batidaDao = batidaDao
    )

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
        revocationHandler = deviceRevocationHandler
    )

    val deviceAuthRepository: DeviceAuthRepository =
        RemoteDeviceAuthRepository(context, polarisApiService, credentialsStore, colaboradorSyncRepository, batidaDao)
}
