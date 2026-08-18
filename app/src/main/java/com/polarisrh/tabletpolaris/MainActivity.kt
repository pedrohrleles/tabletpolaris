package com.polarisrh.tabletpolaris

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.polarisrh.tabletpolaris.navigation.PolarisNavGraph
import com.polarisrh.tabletpolaris.ui.theme.TabletPolarisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as PolarisApplication).container

        setContent {
            TabletPolarisTheme {
                PolarisNavGraph(container = container)
            }
        }
    }
}
