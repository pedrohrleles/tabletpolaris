package com.polarisrh.tabletpolaris.ui.screens.facial

import android.graphics.Bitmap
import android.graphics.Rect as AndroidRect
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoDao
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import com.polarisrh.tabletpolaris.facial.FaceDetectionStatus
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
import com.polarisrh.tabletpolaris.ui.components.AnimatedCheckmark
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

/** Fixa a altura do rodapé — sem isso, o Cancelar mudando de altura (habilitado/desabilitado)
 *  empurraria a área da câmera (e o oval) de tamanho. Conteúdo fica centralizado verticalmente
 *  dentro desse espaço fixo. Só o botão Cancelar mora aqui — status e erro agora aparecem
 *  embaixo do oval, na própria câmera. */
private val FooterHeight = 96.dp

@Composable
fun FacialCapturePlaceholderScreen(
    matricula: String,
    modo: ModoCaptura,
    punchRepository: PunchRepository,
    colaboradorDao: ColaboradorDao,
    tentativaReconhecimentoDao: TentativaReconhecimentoDao,
    credentialsStore: DeviceCredentialsStore,
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
                    matricula,
                    modo,
                    punchRepository,
                    colaboradorDao,
                    tentativaReconhecimentoDao,
                    credentialsStore,
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
    var nomeColaborador by remember { mutableStateOf<String?>(null) }
    var capturarFrameBruto by remember { mutableStateOf<(() -> Bitmap?)?>(null) }
    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val guideWidthPx = with(density) { FaceGuideSize.width.dp.toPx() }
    val guideHeightPx = with(density) { FaceGuideSize.height.dp.toPx() }

    // Sem botão manual: a lógica de quando disparar (borda de subida, cooldown pós-falha,
    // hold de 3s no cadastro) vive inteira no ViewModel — aqui só conecta a captura de frame
    // e o cálculo do retângulo do oval, pra checagem de enquadramento rodar na mesma imagem
    // exibida (nunca mais um stream de análise separado, com campo de visão diferente). O
    // recorte usado pro embedding em si também é feito pelo ViewModel, ao redor do rosto
    // DETECTADO em cada amostra — não da área fixa do oval — pra similaridade não variar
    // conforme a distância da câmera entre cadastro e reconhecimento.
    LaunchedEffect(matricula) {
        nomeColaborador = colaboradorDao.buscarPorMatricula(matricula)?.nome
    }

    LaunchedEffect(Unit) {
        viewModel.iniciar(
            capturarFrameBruto = { capturarFrameBruto?.invoke() },
            calcularOvalRect = { bitmap -> calcularOvalRect(bitmap, boxSizePx, guideWidthPx, guideHeightPx) },
            onPunchRegistered = onPunchRegistered
        )
    }

    // Cadastro e reconhecimento são fluxos separados: ao cadastrar, mostra a confirmação e
    // manda pra tela de reconhecimento — a batida em si só acontece por lá.
    LaunchedEffect(uiState.cadastroConcluido) {
        if (uiState.cadastroConcluido) {
            delay(DELAY_APOS_CADASTRO_MS)
            onCadastroConcluido()
        }
    }

    if (uiState.cadastroConcluido) {
        FacialCadastradaSucesso(nomeColaborador = nomeColaborador, matricula = matricula)
        return
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
                nomeColaborador?.let { nome ->
                    Text(
                        text = nome,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PolarisOnPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Text(
                    text = "Matrícula: $matricula",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PolarisMuted
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
                        viewModel.removerFacial(aoRemover = onCancel)
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
        // resultado) — os saltos de valor são de verdade (cada amostra colhida a cada
        // ~500-750ms), mas a barra em si interpola visualmente entre um valor e outro em vez
        // de pular, sem atrasar nem alterar o tempo real de nenhuma etapa (é só a
        // representação visual que suaviza).
        val progressoAnimado by animateFloatAsState(
            targetValue = uiState.scanProgress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 400, easing = LinearEasing),
            label = "scanProgress"
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(StatusBarIdleColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progressoAnimado)
                    .background(scanColorFor(progressoAnimado))
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
                .onSizeChanged { boxSizePx = it }
        ) {
            FrontCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onCapturaDisponivel = { capturarFrameBruto = it }
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

            // Status logo abaixo do oval, sobre a área já escurecida da câmera — mais fácil de
            // ler olhando pra câmera durante o cadastro/reconhecimento do que uma mensagem lá
            // embaixo, longe do rosto na tela. A mensagem de erro (ex.: "Rosto não
            // reconhecido") entra aqui também, com prioridade abaixo das mensagens de
            // enquadramento — assim, se o rosto sair de posição, a dica de enquadramento (mais
            // acionável no momento) toma a frente; se continuar bem enquadrado (é o que
            // acontece durante o cooldown pós-falha, antes da nova tentativa automática), mostra
            // o erro em vez do "Rosto posicionado corretamente" genérico.
            val (statusMessage, statusColor) = when {
                uiState.isScanning && isCadastro -> "Mantenha o rosto parado..." to PolarisSuccess
                uiState.isScanning -> "Verificando rosto..." to PolarisSuccess
                uiState.faceDetectionStatus == FaceDetectionStatus.SemRosto -> "Nenhum rosto detectado" to PolarisOnPrimary
                uiState.faceDetectionStatus == FaceDetectionStatus.MultiplosRostos -> "Mais de um rosto detectado" to PolarisOnPrimary
                uiState.faceDetectionStatus == FaceDetectionStatus.RostoDistante -> "Aproxime-se da câmera" to PolarisOnPrimary
                uiState.faceDetectionStatus == FaceDetectionStatus.RostoPerto -> "Afaste-se um pouco da câmera" to PolarisOnPrimary
                uiState.faceDetectionStatus == FaceDetectionStatus.ForaDoCentro -> "Centralize o rosto na área indicada" to PolarisOnPrimary
                uiState.errorMessage != null -> uiState.errorMessage to MaterialTheme.colorScheme.error
                else -> "Rosto posicionado corretamente" to PolarisSuccess
            }
            Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (FaceGuideSize.height / 2f).dp + 48.dp)
                    .fillMaxWidth(0.85f)
            )
        }

        // Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(FooterHeight)
                .background(PolarisSurfaceDark)
                .navigationBarsPadding()
                .padding(horizontal = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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

/** Tela cheia de confirmação, no mesmo estilo do "Ponto registrado!" — mais fácil de entender
 *  que o cadastro terminou do que só um texto verde por cima da câmera. */
@Composable
private fun FacialCadastradaSucesso(nomeColaborador: String?, matricula: String) {
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
            AnimatedCheckmark(color = PolarisSuccess)

            Text(
                text = "Facial cadastrada com sucesso!",
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp)
            )
            nomeColaborador?.let { nome ->
                Text(
                    text = nome,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PolarisMuted,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            Text(
                text = "Matrícula $matricula",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                modifier = Modifier.padding(top = if (nomeColaborador != null) 4.dp else 12.dp)
            )
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

/**
 * Calcula, pra um bitmap dado, o retângulo ocupado pelo oval-guia nesse MESMO bitmap — mesma
 * proporção usada pra desenhar o guia na tela, escalada caso o bitmap capturado não tenha
 * exatamente o mesmo tamanho em pixels do Box (ex.: PreviewView.bitmap pode diferir um pouco).
 * Usado tanto pra recortar o rosto pro embedding quanto pra checar o enquadramento — sempre a
 * mesma imagem, nunca um stream separado da câmera com campo de visão diferente.
 */
private fun calcularOvalRect(bitmap: Bitmap, boxSizePx: IntSize, guideWidthPx: Float, guideHeightPx: Float): AndroidRect {
    val escalaX = bitmap.width.toFloat() / boxSizePx.width
    val escalaY = bitmap.height.toFloat() / boxSizePx.height

    val largura = (guideWidthPx * escalaX).toInt().coerceIn(1, bitmap.width)
    val altura = (guideHeightPx * escalaY).toInt().coerceIn(1, bitmap.height)
    val x = ((bitmap.width - largura) / 2).coerceIn(0, bitmap.width - largura)
    val y = ((bitmap.height - altura) / 2).coerceIn(0, bitmap.height - altura)

    return AndroidRect(x, y, x + largura, y + altura)
}
