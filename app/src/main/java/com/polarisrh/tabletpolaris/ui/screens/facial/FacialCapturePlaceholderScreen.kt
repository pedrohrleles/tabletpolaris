package com.polarisrh.tabletpolaris.ui.screens.facial

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import com.polarisrh.tabletpolaris.facial.FaceDetectionStatus
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
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
import kotlinx.coroutines.delay

private val FaceGuideSize = Size(width = 380f, height = 520f)
private val StatusBarIdleColor = PolarisMuted.copy(alpha = 0.25f)
private const val DELAY_APOS_CADASTRO_MS = 1500L

@Composable
fun FacialCapturePlaceholderScreen(
    matricula: String,
    modo: ModoCaptura,
    punchRepository: PunchRepository,
    colaboradorDao: ColaboradorDao,
    faceEmbeddingExtractor: FaceEmbeddingExtractor,
    deviceStatusChecker: DeviceStatusChecker,
    networkMonitor: NetworkMonitor,
    onPunchRegistered: (PunchResult) -> Unit,
    onCadastroConcluido: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel: FacialCaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                FacialCaptureViewModel(
                    modo,
                    punchRepository,
                    colaboradorDao,
                    faceEmbeddingExtractor,
                    deviceStatusChecker,
                    networkMonitor
                )
            }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val isCadastro = modo == ModoCaptura.CADASTRO
    var showMenuDebug by remember { mutableStateOf(false) }
    var capturarFrame by remember { mutableStateOf<(() -> Bitmap?)?>(null) }

    // Sem botão manual: assim que o enquadramento fica "pronto", dispara sozinho.
    LaunchedEffect(uiState.faceDetectionStatus, uiState.isScanning, uiState.cadastroConcluido) {
        if (uiState.faceDetectionStatus == FaceDetectionStatus.Pronto &&
            !uiState.isScanning &&
            !uiState.cadastroConcluido
        ) {
            viewModel.startScan(
                matricula = matricula,
                capturarFrame = { capturarFrame?.invoke() },
                onSuccess = onPunchRegistered
            )
        }
    }

    // Cadastro e reconhecimento são fluxos separados: ao cadastrar, mostra a confirmação e
    // manda pra tela de reconhecimento — a batida em si só acontece por lá.
    LaunchedEffect(uiState.cadastroConcluido) {
        if (uiState.cadastroConcluido) {
            delay(DELAY_APOS_CADASTRO_MS)
            onCadastroConcluido()
        }
    }

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
                    text = if (isCadastro) "Cadastrar Facial" else "Reconhecimento Facial",
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
            // Menu temporário de debug — só no reconhecimento, remove quando não precisar mais.
            PolarisLogoMark(
                size = 64.dp,
                modifier = if (isCadastro) Modifier else Modifier.clickable { showMenuDebug = true }
            )
        }

        if (showMenuDebug) {
            AlertDialog(
                onDismissRequest = { showMenuDebug = false },
                title = { Text("Remover facial (debug)") },
                text = { Text("Remover o cadastro facial da matrícula $matricula? Ela vai precisar se recadastrar.") },
                confirmButton = {
                    TextButton(onClick = {
                        showMenuDebug = false
                        viewModel.removerFacial(matricula, aoRemover = onCancel)
                    }) {
                        Text("Sim")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMenuDebug = false }) {
                        Text("Não")
                    }
                }
            )
        }

        // A barra acompanha o progresso real do pipeline (captura → embedding → checagem →
        // resultado), não uma animação — cada salto é uma etapa de verdade concluída.
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

        // Live front-camera feed com detecção facial (ML Kit) rodando a cada frame — só
        // classifica o enquadramento (nenhum rosto / mais de um / longe / pronto).
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(PolarisCameraPlaceholder)
        ) {
            FrontCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onFaceDetectionStatus = viewModel::atualizarStatusDeteccao,
                onCapturaDisponivel = { capturarFrame = it }
            )

            // Spotlight: dim everything outside the guide, punch a clear hole where the
            // face should go, then draw the guide outline on top — verde quando o
            // enquadramento está bom o bastante pra capturar.
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

                val corGuia = if (uiState.faceDetectionStatus == FaceDetectionStatus.Pronto) {
                    PolarisSuccess
                } else {
                    PolarisBlue
                }
                drawOval(
                    color = corGuia,
                    topLeft = ovalTopLeft,
                    size = ovalSize,
                    style = Stroke(width = 5.dp.toPx())
                )
            }
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
                text = when {
                    uiState.cadastroConcluido -> "Facial cadastrada com sucesso!"
                    uiState.isScanning && isCadastro -> "Cadastrando, aguarde..."
                    uiState.isScanning -> "Identificando, aguarde..."
                    uiState.faceDetectionStatus == FaceDetectionStatus.SemRosto -> "Nenhum rosto detectado"
                    uiState.faceDetectionStatus == FaceDetectionStatus.MultiplosRostos -> "Mais de um rosto detectado"
                    uiState.faceDetectionStatus == FaceDetectionStatus.RostoDistante -> "Aproxime-se da câmera"
                    uiState.faceDetectionStatus == FaceDetectionStatus.ForaDoCentro -> "Centralize o rosto na área indicada"
                    else -> "Rosto posicionado corretamente"
                },
                style = MaterialTheme.typography.titleMedium,
                color = if (uiState.cadastroConcluido) PolarisSuccess else PolarisOnPrimary,
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

            TextButton(onClick = onCancel, enabled = !uiState.isScanning && !uiState.cadastroConcluido) {
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
