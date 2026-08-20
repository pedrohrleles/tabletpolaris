package com.polarisrh.tabletpolaris.ui.screens.clockin

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.ui.components.NumericKeypad
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted

/** Safety cap only — matrícula is a growing numeric id (1, 2, 3, ...), not a fixed-length code. */
private const val MAX_MATRICULA_LENGTH = 10

@Composable
fun ClockInScreen(
    deviceStatusChecker: DeviceStatusChecker,
    networkMonitor: NetworkMonitor,
    onMatriculaConfirmed: (String) -> Unit
) {
    // Não usa UI state daqui — só mantém vivo o polling de status (30s) e a checagem ao
    // recuperar rede enquanto essa tela estiver aberta.
    viewModel<ClockInViewModel>(
        factory = viewModelFactory {
            initializer { ClockInViewModel(deviceStatusChecker, networkMonitor) }
        }
    )

    var matricula by remember { mutableStateOf("") }

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
            Text(text = "Bater Ponto", style = MaterialTheme.typography.headlineLarge)
            Text(
                text = "Digite sua matrícula para registrar o ponto",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = 12.dp, bottom = 40.dp)
            )

            Text(
                text = "MATRÍCULA",
                style = MaterialTheme.typography.labelMedium,
                color = PolarisMuted,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp, start = 4.dp)
            )

            MatriculaField(matricula = matricula)

            Spacer(modifier = Modifier.height(36.dp))

            NumericKeypad(
                onDigit = { digit ->
                    if (matricula.length < MAX_MATRICULA_LENGTH) {
                        matricula += digit
                    }
                },
                onBackspace = {
                    matricula = matricula.dropLast(1)
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = { onMatriculaConfirmed(matricula) },
                enabled = matricula.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                Text("Confirmar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MatriculaField(matricula: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 26.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = matricula,
                style = MaterialTheme.typography.headlineSmall
            )
            BlinkingCursor()
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "matricula_cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1000
                1f at 0
                1f at 499
                0f at 500
                0f at 999
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "matricula_cursor_alpha"
    )
    Box(
        modifier = Modifier
            .padding(start = 6.dp)
            .size(width = 3.dp, height = 40.dp)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
}
