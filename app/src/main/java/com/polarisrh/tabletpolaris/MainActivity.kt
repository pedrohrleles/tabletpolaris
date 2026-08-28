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
        iniciarFixacaoDeTela()

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
            // Reabrir o app (Android manteve o processo vivo em segundo plano depois de um
            // desafixe) só chama onResume/aqui, nunca onCreate de novo — sem isso, só a
            // primeira abertura do processo ficava fixada, e sair uma vez "destravava" pro
            // resto da sessão.
            iniciarFixacaoDeTela()
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

    /**
     * Fixa a tela sozinho, sem precisar que alguém abra "Apps recentes" e escolha "Fixar" na
     * mão — decisão do time: sem PIN de desafixar (exige senha de tela no aparelho, que também
     * passaria a ser pedida em todo reinício, deixando quem bate ponto refém de alguém saber a
     * senha). Sem PIN, "Voltar + Apps recentes" ainda desafixa — aceito de propósito: essa trava
     * é só pra não sobrar na tela do Android por acidente, não pra barrar alguém decidido a sair.
     * Precisa da opção "Fixar app" ativada nas configurações do tablet (uma vez, na ativação).
     */
    private fun iniciarFixacaoDeTela() {
        startLockTask()
    }
}
