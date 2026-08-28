package com.polarisrh.tabletpolaris.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.polarisrh.tabletpolaris.AppContainer
import com.polarisrh.tabletpolaris.ui.screens.clockin.ClockInScreen
import com.polarisrh.tabletpolaris.ui.screens.confirm.IdentityConfirmationScreen
import com.polarisrh.tabletpolaris.ui.screens.facial.FacialCapturePlaceholderScreen
import com.polarisrh.tabletpolaris.ui.screens.facial.ModoCaptura
import com.polarisrh.tabletpolaris.ui.screens.setup.DeviceSetupScreen
import com.polarisrh.tabletpolaris.ui.screens.splash.SplashScreen
import com.polarisrh.tabletpolaris.ui.screens.success.PunchSuccessScreen

object PolarisDestinations {
    const val SPLASH = "splash"
    const val DEVICE_SETUP = "device_setup"
    const val CLOCK_IN = "clock_in"
    const val IDENTITY_CONFIRMATION = "confirmar_identidade/{matricula}"
    const val FACIAL_CAPTURE = "facial_capture/{matricula}"
    const val FACIAL_ENROLLMENT = "cadastro_facial/{matricula}"
    const val PUNCH_SUCCESS = "punch_success/{matricula}/{timestamp}"

    fun identityConfirmation(matricula: String) = "confirmar_identidade/$matricula"
    fun facialCapture(matricula: String) = "facial_capture/$matricula"
    fun facialEnrollment(matricula: String) = "cadastro_facial/$matricula"
    fun punchSuccess(matricula: String, timestampMillis: Long) = "punch_success/$matricula/$timestampMillis"
}

/**
 * Um toque duplo (ou multi-touch) num botão que navega dispara o onClick mais de uma vez ANTES
 * da transição do primeiro toque terminar e remover aquele botão da composição — cada chamada
 * de navigate()/popBackStack() executa de verdade. Com popBackStack() isso já causou o back
 * stack esvaziar por completo (ex.: Cancelar 2x rápido também removia a tela de Bater Ponto, já
 * que ela ainda não tinha virado o destino "atual" de fato — o NavHost ficava sem destino
 * nenhum pra renderizar, só o fundo escuro do Surface, sem nada clicável pra sair de lá). O
 * destino atual só fica RESUMED depois que a transição em andamento assenta, então checar isso
 * aqui filtra o toque duplicado sem precisar de nenhum debounce manual.
 */
private fun NavHostController.currentEntryIsResumed() =
    currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED

@Composable
fun PolarisNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
    // Se o backend disser (via heartbeat) que este tablet foi desativado, larga o que
    // estiver na tela e volta pra ativação — de qualquer ponto do app, a qualquer momento.
    val revocationMessage by container.deviceRevocationMessage.collectAsState()
    LaunchedEffect(revocationMessage) {
        if (revocationMessage != null && navController.currentDestination?.route != PolarisDestinations.DEVICE_SETUP) {
            navController.navigate(PolarisDestinations.DEVICE_SETUP) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        NavHost(navController = navController, startDestination = PolarisDestinations.SPLASH) {

            composable(PolarisDestinations.SPLASH) {
                SplashScreen(
                    deviceAuthRepository = container.deviceAuthRepository,
                    desativacaoHandler = container.desativacaoHandler,
                    onDeviceProvisioned = {
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.SPLASH) { inclusive = true }
                        }
                    },
                    onDeviceNotProvisioned = {
                        navController.navigate(PolarisDestinations.DEVICE_SETUP) {
                            popUpTo(PolarisDestinations.SPLASH) { inclusive = true }
                        }
                    }
                )
            }

            composable(PolarisDestinations.DEVICE_SETUP) {
                val pendingRevocationMessage = container.deviceRevocationMessage.value
                LaunchedEffect(Unit) {
                    container.deviceRevocationMessage.value = null
                }
                DeviceSetupScreen(
                    deviceAuthRepository = container.deviceAuthRepository,
                    initialErrorMessage = pendingRevocationMessage,
                    onDeviceLinked = {
                        if (navController.currentEntryIsResumed()) {
                            navController.navigate(PolarisDestinations.CLOCK_IN) {
                                popUpTo(PolarisDestinations.DEVICE_SETUP) { inclusive = true }
                            }
                        }
                    }
                )
            }

            composable(PolarisDestinations.CLOCK_IN) {
                ClockInScreen(
                    deviceStatusChecker = container.deviceStatusChecker,
                    colaboradorSyncRepository = container.colaboradorSyncRepository,
                    networkMonitor = container.networkMonitor,
                    colaboradorDao = container.colaboradorDao,
                    credentialsStore = container.credentialsStore,
                    desativacaoHandler = container.desativacaoHandler,
                    onReconhecerFacial = { matricula ->
                        if (navController.currentEntryIsResumed()) {
                            navController.navigate(PolarisDestinations.facialCapture(matricula))
                        }
                    },
                    onPrecisarConfirmarIdentidade = { matricula ->
                        if (navController.currentEntryIsResumed()) {
                            navController.navigate(PolarisDestinations.identityConfirmation(matricula))
                        }
                    }
                )
            }

            composable(
                route = PolarisDestinations.IDENTITY_CONFIRMATION,
                arguments = listOf(navArgument("matricula") { type = NavType.StringType })
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                IdentityConfirmationScreen(
                    matricula = matricula,
                    colaboradorDao = container.colaboradorDao,
                    onConfirmado = {
                        if (navController.currentEntryIsResumed()) {
                            navController.navigate(PolarisDestinations.facialEnrollment(matricula))
                        }
                    },
                    onNegado = { if (navController.currentEntryIsResumed()) navController.popBackStack() }
                )
            }

            composable(
                route = PolarisDestinations.FACIAL_CAPTURE,
                arguments = listOf(navArgument("matricula") { type = NavType.StringType })
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                FacialCapturePlaceholderScreen(
                    matricula = matricula,
                    modo = ModoCaptura.RECONHECIMENTO,
                    punchRepository = container.punchRepository,
                    colaboradorDao = container.colaboradorDao,
                    tentativaReconhecimentoDao = container.tentativaReconhecimentoDao,
                    credentialsStore = container.credentialsStore,
                    faceEmbeddingExtractor = container.faceEmbeddingExtractor,
                    audioPlayer = container.audioPlayer,
                    onPunchRegistered = { punchResult ->
                        val timestampMillis = punchResult.timestamp.toEpochMilli()
                        navController.navigate(PolarisDestinations.punchSuccess(matricula, timestampMillis)) {
                            popUpTo(PolarisDestinations.CLOCK_IN)
                        }
                    },
                    onCadastroConcluido = {},
                    onCancel = { if (navController.currentEntryIsResumed()) navController.popBackStack() },
                    onTimeout = {
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.CLOCK_IN) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = PolarisDestinations.FACIAL_ENROLLMENT,
                arguments = listOf(navArgument("matricula") { type = NavType.StringType })
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                FacialCapturePlaceholderScreen(
                    matricula = matricula,
                    modo = ModoCaptura.CADASTRO,
                    punchRepository = container.punchRepository,
                    colaboradorDao = container.colaboradorDao,
                    tentativaReconhecimentoDao = container.tentativaReconhecimentoDao,
                    credentialsStore = container.credentialsStore,
                    faceEmbeddingExtractor = container.faceEmbeddingExtractor,
                    audioPlayer = container.audioPlayer,
                    // Cadastro nunca bate ponto — só gera e salva o embedding.
                    onPunchRegistered = {},
                    onCadastroConcluido = {
                        // Fluxo separado: depois de cadastrar, manda pro reconhecimento de
                        // verdade, que é quem efetivamente registra a batida.
                        navController.navigate(PolarisDestinations.facialCapture(matricula)) {
                            popUpTo(PolarisDestinations.CLOCK_IN)
                        }
                    },
                    onCancel = { if (navController.currentEntryIsResumed()) navController.popBackStack() },
                    onTimeout = {
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.CLOCK_IN) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = PolarisDestinations.PUNCH_SUCCESS,
                arguments = listOf(
                    navArgument("matricula") { type = NavType.StringType },
                    navArgument("timestamp") { type = NavType.LongType }
                )
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                val timestampMillis = backStackEntry.arguments?.getLong("timestamp") ?: 0L
                PunchSuccessScreen(
                    matricula = matricula,
                    timestampMillis = timestampMillis,
                    audioPlayer = container.audioPlayer,
                    credentialsStore = container.credentialsStore,
                    onTimeout = {
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.CLOCK_IN) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}
