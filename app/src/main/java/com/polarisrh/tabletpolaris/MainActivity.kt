package com.polarisrh.tabletpolaris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.polarisrh.tabletpolaris.navigation.PolarisNavGraph
import com.polarisrh.tabletpolaris.ui.theme.TabletPolarisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        enterKioskImmersiveMode()

        val container = (application as PolarisApplication).container

        setContent {
            TabletPolarisTheme {
                PolarisNavGraph(container = container)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enterKioskImmersiveMode()
        }
    }

    /**
     * Fixed kiosk tablet: the app should own the whole screen, like a game. System bars
     * only reappear on an edge swipe and auto-hide again — they're never permanently shown.
     */
    private fun enterKioskImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
