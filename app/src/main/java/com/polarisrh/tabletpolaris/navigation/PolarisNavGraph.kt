package com.polarisrh.tabletpolaris.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.polarisrh.tabletpolaris.AppContainer
import com.polarisrh.tabletpolaris.ui.screens.clockin.ClockInScreen
import com.polarisrh.tabletpolaris.ui.screens.facial.FacialCapturePlaceholderScreen
import com.polarisrh.tabletpolaris.ui.screens.setup.DeviceSetupScreen
import com.polarisrh.tabletpolaris.ui.screens.splash.SplashScreen
import com.polarisrh.tabletpolaris.ui.screens.success.PunchSuccessScreen

object PolarisDestinations {
    const val SPLASH = "splash"
    const val DEVICE_SETUP = "device_setup"
    const val CLOCK_IN = "clock_in"
    const val FACIAL_CAPTURE = "facial_capture/{matricula}"
    const val PUNCH_SUCCESS = "punch_success/{matricula}"

    fun facialCapture(matricula: String) = "facial_capture/$matricula"
    fun punchSuccess(matricula: String) = "punch_success/$matricula"
}

@Composable
fun PolarisNavGraph(
    container: AppContainer,
    navController: NavHostController = rememberNavController()
) {
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
                DeviceSetupScreen(
                    deviceAuthRepository = container.deviceAuthRepository,
                    onDeviceLinked = {
                        navController.navigate(PolarisDestinations.CLOCK_IN) {
                            popUpTo(PolarisDestinations.DEVICE_SETUP) { inclusive = true }
                        }
                    }
                )
            }

            composable(PolarisDestinations.CLOCK_IN) {
                ClockInScreen(
                    onMatriculaConfirmed = { matricula ->
                        navController.navigate(PolarisDestinations.facialCapture(matricula))
                    }
                )
            }

            composable(
                route = PolarisDestinations.FACIAL_CAPTURE,
                arguments = listOf(navArgument("matricula") { type = NavType.StringType })
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                FacialCapturePlaceholderScreen(
                    matricula = matricula,
                    punchRepository = container.punchRepository,
                    onPunchRegistered = {
                        navController.navigate(PolarisDestinations.punchSuccess(matricula)) {
                            popUpTo(PolarisDestinations.CLOCK_IN)
                        }
                    },
                    onCancel = { navController.popBackStack() }
                )
            }

            composable(
                route = PolarisDestinations.PUNCH_SUCCESS,
                arguments = listOf(navArgument("matricula") { type = NavType.StringType })
            ) { backStackEntry ->
                val matricula = backStackEntry.arguments?.getString("matricula").orEmpty()
                PunchSuccessScreen(
                    matricula = matricula,
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
