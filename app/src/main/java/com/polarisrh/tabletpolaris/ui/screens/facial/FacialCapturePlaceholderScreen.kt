package com.polarisrh.tabletpolaris.ui.screens.facial

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import com.polarisrh.tabletpolaris.ui.components.FrontCameraPreview
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.theme.PolarisBlue
import com.polarisrh.tabletpolaris.ui.theme.PolarisCameraPlaceholder
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess
import com.polarisrh.tabletpolaris.ui.theme.PolarisSurfaceDark
import com.polarisrh.tabletpolaris.ui.theme.PolarisWarning

private val FaceGuideSize = Size(width = 380f, height = 520f)
private val StatusBarIdleColor = PolarisMuted.copy(alpha = 0.25f)

@Composable
fun FacialCapturePlaceholderScreen(
    matricula: String,
    punchRepository: PunchRepository,
    deviceStatusChecker: DeviceStatusChecker,
    networkMonitor: NetworkMonitor,
    onPunchRegistered: (PunchResult) -> Unit,
    onCancel: () -> Unit
) {
    val viewModel: FacialCaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FacialCaptureViewModel(punchRepository, deviceStatusChecker, networkMonitor) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {

        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PolarisSurfaceDark)
                .statusBarsPadding()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Reconhecimento Facial",
                    style = MaterialTheme.typography.headlineMedium,
                    color = PolarisOnPrimary
                )
                Text(
                    text = "Matrícula: $matricula",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PolarisMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            PolarisLogoMark(size = 64.dp)
        }

        // Thin progress strip glued to the top edge of the camera area: a grey track that
        // fills left-to-right as the scan advances, red -> orange -> green.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(StatusBarIdleColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = uiState.scanProgress.coerceIn(0f, 1f))
                    .background(scanColorFor(uiState.scanProgress))
            )
        }

        // Live front-camera feed. No face detection/recognition wired in yet — the guide
        // and caption below are just an overlay floating on top of the real preview.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(PolarisCameraPlaceholder)
        ) {
            FrontCameraPreview(modifier = Modifier.fillMaxSize())

            // Spotlight: dim everything outside the guide, punch a clear hole where the
            // face should go, then draw the guide outline on top.
            Canvas(modifier = Modifier.fillMaxSize()) {
                val guideWidthPx = FaceGuideSize.width.dp.toPx()
                val guideHeightPx = FaceGuideSize.height.dp.toPx()
                val ovalTopLeft = Offset(
                    x = (size.width - guideWidthPx) / 2f,
                    y = (size.height - guideHeightPx) / 2f
                )
                val ovalSize = Size(guideWidthPx, guideHeightPx)

                drawContext.canvas.saveLayer(Rect(Offset.Zero, size), Paint())
                drawRect(color = Color.Black.copy(alpha = 0.55f))
                drawOval(
                    color = Color.Transparent,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    blendMode = BlendMode.Clear
                )
                drawContext.canvas.restore()

                drawOval(
                    color = PolarisBlue,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 5.dp.toPx())
                )
            }

            Text(
                text = "Reconhecimento automático\nserá integrado em uma próxima fase",
                style = MaterialTheme.typography.bodyMedium,
                color = PolarisMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
            )
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PolarisSurfaceDark)
                .navigationBarsPadding()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (uiState.isScanning) {
                    "Identificando, aguarde..."
                } else {
                    "Posicione o rosto dentro da área indicada"
                },
                style = MaterialTheme.typography.titleMedium,
                color = PolarisOnPrimary,
                textAlign = TextAlign.Center
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Button(
                onClick = { viewModel.startScan(matricula, onPunchRegistered) },
                enabled = !uiState.isScanning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                Text(
                    text = if (uiState.isScanning) "Identificando..." else "Simular reconhecimento",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(onClick = onCancel, enabled = !uiState.isScanning) {
                Text(
                    "Cancelar",
                    style = MaterialTheme.typography.bodyLarge,
                    color = PolarisOnPrimary
                )
            }
        }
    }
}

private fun scanColorFor(progress: Float): Color {
    val clamped = progress.coerceIn(0f, 1f)
    return if (clamped < 0.5f) {
        lerp(PolarisError, PolarisWarning, clamped / 0.5f)
    } else {
        lerp(PolarisWarning, PolarisSuccess, (clamped - 0.5f) / 0.5f)
    }
}
