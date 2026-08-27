package com.polarisrh.tabletpolaris.ui.screens.success

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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

    Box(modifier = Modifier.fillMaxSize()) {
        PolarisLogoMark(
            size = 64.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(48.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AnimatedCheckmark()

            Text(
                text = "Ponto registrado!",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 32.dp)
            )
            Text(
                text = "Matrícula $matricula",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = formattedTimestamp,
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
