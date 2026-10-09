package com.polarisrh.tabletpolaris.ui.screens.success

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.audio.PolarisAudioPlayer
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.ui.components.AnimatedCheckmark
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val AUTO_RETURN_DELAY_MS = 4000L
private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")

@Composable
fun PunchSuccessScreen(
    matricula: String,
    timestampMillis: Long,
    audioPlayer: PolarisAudioPlayer,
    credentialsStore: DeviceCredentialsStore,
    onTimeout: () -> Unit
) {
    LaunchedEffect(matricula) {
        audioPlayer.tocarPontoRegistrado()
        delay(AUTO_RETURN_DELAY_MS)
        onTimeout()
    }

    val formattedTimestamp = remember(timestampMillis) {
        // Mesmo fuso do local de trabalho registrado usado no relógio de "Bater ponto" — cai
        // pro fuso do próprio tablet só se o local não tiver essa informação.
        val zoneId = credentialsStore.read()?.timezone
            ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
            ?: ZoneId.systemDefault()
        Instant.ofEpochMilli(timestampMillis)
            .atZone(zoneId)
            .format(TIMESTAMP_FORMATTER)
    }

    // Mesmos tamanhos fixos (padding 48dp, checkmark 140dp) que, no "Bater Ponto", sobrepunham
    // conteúdo e cortavam botão em tablets menores — aqui não tem botão pra cortar, mas vale
    // manter o mesmo tratamento responsivo por consistência entre telas.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compacto = maxHeight < 1100.dp || maxWidth < 700.dp
        val paddingTela = if (compacto) 24.dp else 48.dp
        val tamanhoLogo = if (compacto) 48.dp else 64.dp
        val tamanhoCheck = if (compacto) 104.dp else 140.dp

        PolarisLogoMark(
            size = tamanhoLogo,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(paddingTela)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingTela),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedCheckmark(size = tamanhoCheck)

            Text(
                text = "Ponto registrado!",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = "Matrícula $matricula",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
