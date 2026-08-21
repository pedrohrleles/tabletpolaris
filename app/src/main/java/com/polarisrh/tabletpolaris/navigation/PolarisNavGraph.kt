package com.polarisrh.tabletpolaris.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.polarisrh.tabletpolaris.AppContainer
import com.polarisrh.tabletpolaris.ui.screens.clockin.ClockInScreen
import com.polarisrh.tabletpolaris.ui.screens.confirm.IdentityConfirmationScreen
import com.polarisrh.tabletpolaris.ui.screens.debug.DatabaseViewerScreen
import com.polarisrh.tabletpolaris.ui.screens.facial.FacialCapturePlaceholderScreen
import com.polarisrh.tabletpolaris.ui.screens.facial.ModoCaptura
import com.polarisrh.tabletpolaris.ui.screens.setup.DeviceSetupScreen
import com.polarisrh.tabletpolaris.ui.screens.splash.SplashScreen
import com.polarisrh.tabletpolaris.ui.screens.success.PunchSuccessScreen
import java.time.ZoneId

object PolarisDestinations {
    const val SPLASH = "splash"
    const val DEVICE_SETUP = "device_setup"
    const val CLOCK_IN = "clock_in"
    const val IDENTITY_CONFIRMATION = "confirmar_identidade/{matricula}"
    const val FACIAL_CAPTURE = "facial_capture/{matricula}"
    const val FACIAL_ENROLLMENT = "cadastro_facial/{matricula}"
    const val PUNCH_SUCCESS = "punch_success/{matricula}/{timestamp}"
    const val DATABASE_VIEWER = "database_viewer"

    fun identityConfirmation(matricula: String) = "confirmar_identidade/$matricula"
    fun facialCapture(matricula: String) = "facial_capture/$matricula"
    fun facialEnrollment(matricula: String) = "cadastro_facial/$matricula"
    fun punchSuccess(matricula: String, timestampMillis: Long) = "punch_success/$matricula/$timestampMillis"
}

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
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.DEVICE_SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(PolarisDestinations.CLOCK_IN) {
                ClockInScreen(
                    deviceStatusChecker = container.deviceStatusChecker,
                    networkMonitor = container.networkMonitor,
                    colaboradorDao = container.colaboradorDao,
                    onReconhecerFacial = { matricula ->
                        navController.navigate(PolarisDestinations.facialCapture(matricula))
                    },
                    onPrecisarConfirmarIdentidade = { matricula ->
                        navController.navigate(PolarisDestinations.identityConfirmation(matricula))
                    },
                    onAbrirBancoDeDados = {
                        navController.navigate(PolarisDestinations.DATABASE_VIEWER)
                    },
                    onSairAtivacao = {
                        container.credentialsStore.clear()
                        navController.navigate(PolarisDestinations.DEVICE_SETUP) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(PolarisDestinations.DATABASE_VIEWER) {
                DatabaseViewerScreen(
                    colaboradorDao = container.colaboradorDao,
                    batidaPendenteDao = container.batidaPendenteDao,
                    onBack = { navController.popBackStack() }
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
                        navController.navigate(PolarisDestinations.facialEnrollment(matricula))
                    },
                    onNegado = { navController.popBackStack() }
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
                    faceEmbeddingExtractor = container.faceEmbeddingExtractor,
                    deviceStatusChecker = container.deviceStatusChecker,
                    networkMonitor = container.networkMonitor,
                    onPunchRegistered = { punchResult ->
                        val timestampMillis = punchResult.timestamp
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        navController.navigate(PolarisDestinations.punchSuccess(matricula, timestampMillis)) {
                            popUpTo(PolarisDestinations.CLOCK_IN)
                        }
                    },
                    onCadastroConcluido = {},
                    onCancel = { navController.popBackStack() }
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
                    faceEmbeddingExtractor = container.faceEmbeddingExtractor,
                    deviceStatusChecker = container.deviceStatusChecker,
                    networkMonitor = container.networkMonitor,
                    // Cadastro nunca bate ponto — só gera e salva o embedding.
                    onPunchRegistered = {},
                    onCadastroConcluido = {
                        // Fluxo separado: depois de cadastrar, manda pro reconhecimento de
                        // verdade, que é quem efetivamente registra a batida.
                        navController.navigate(PolarisDestinations.facialCapture(matricula)) {
                            popUpTo(PolarisDestinations.CLOCK_IN)
                        }
                    },
                    onCancel = { navController.popBackStack() }
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
