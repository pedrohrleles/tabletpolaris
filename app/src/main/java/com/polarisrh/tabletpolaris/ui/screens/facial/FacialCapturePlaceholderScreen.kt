package com.polarisrh.tabletpolaris.ui.screens.facial

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.audio.PolarisAudioPlayer
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
import com.polarisrh.tabletpolaris.ui.theme.PolarisCameraPlaceholder
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess
import com.polarisrh.tabletpolaris.ui.theme.PolarisSurfaceDark
import com.polarisrh.tabletpolaris.ui.theme.PolarisWarning
import kotlinx.coroutines.delay

private val StatusBarIdleColor = PolarisMuted.copy(alpha = 0.25f)

// Curto o bastante pra não atrasar o fluxo (depois disso vai automático pro reconhecimento),
// mas longo o suficiente pra dar tempo do AnimatedCheckmark (spring + traço desenhado, ~1s no
// total) terminar de brincar antes da tela trocar — 1500ms cortava a animação pela metade.
private const val DELAY_APOS_CADASTRO_MS = 2500L

/** Ninguém deve ficar preso indefinidamente nessas telas (câmera ligada, matrícula exposta) se
 *  esquecer de cancelar — conta a partir da entrada nesta tela; volta pra "Bater Ponto"
 *  automaticamente se ninguém bater o ponto (ou cadastrar) nem cancelar antes disso. */
private const val TIMEOUT_INATIVIDADE_MS = 60_000L

/** Fixa a altura do rodapé — sem isso, o Cancelar mudando de altura (habilitado/desabilitado)
 *  empurraria a área da câmera de tamanho. Conteúdo fica centralizado verticalmente dentro
 *  desse espaço fixo. Só o botão Cancelar mora aqui — status e erro aparecem sobre a câmera. */
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
    audioPlayer: PolarisAudioPlayer,
    onPunchRegistered: (PunchResult) -> Unit,
    onCadastroConcluido: () -> Unit,
    onCancel: () -> Unit,
    onTimeout: () -> Unit
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

    // Sem botão manual: a lógica de quando disparar (borda de subida, cooldown pós-falha,
    // hold de 3s no cadastro) vive inteira no ViewModel — aqui só conecta a captura de frame.
    // O recorte usado pro embedding é sempre feito ao redor do rosto DETECTADO, não de uma área
    // fixa da tela — pra similaridade não variar conforme a distância da câmera entre cadastro
    // e reconhecimento. Sem guia visual (círculo/oval) nem checagem de tamanho — só detecta
    // presença de rosto; forçar um "encaixe" visual só atrapalhava (zoom degradando nitidez,
    // guia seguindo o rosto ficando esquisito) sem ajudar em nada de verdade.
    LaunchedEffect(matricula) {
        nomeColaborador = colaboradorDao.buscarPorMatricula(matricula)?.nome
    }

    LaunchedEffect(Unit) {
        viewModel.iniciar(
            capturarFrameBruto = { capturarFrameBruto?.invoke() },
            onPunchRegistered = onPunchRegistered
        )
    }

    // Conta a partir da entrada nesta tela (composable novo a cada navegação — sair por
    // "Cancelar" ou por bater o ponto/cadastrar cancela este efeito junto). Sem reset por
    // atividade: são no máximo alguns segundos de captura, então 1min de folga é so pra quem
    // esquece a tela aberta.
    LaunchedEffect(Unit) {
        delay(TIMEOUT_INATIVIDADE_MS)
        onTimeout()
    }

    // Cadastro e reconhecimento são fluxos separados: ao cadastrar, mostra a confirmação e
    // manda pra tela de reconhecimento — a batida em si só acontece por lá.
    LaunchedEffect(uiState.cadastroConcluido) {
        if (uiState.cadastroConcluido) {
            audioPlayer.tocarFacialCadastrada()
            delay(DELAY_APOS_CADASTRO_MS)
            onCadastroConcluido()
        }
    }

    // Crossfade em vez de um if/return direto — trocar de conteúdo dentro da MESMA tela (não é
    // uma navegação de rota) não pega a transição padrão que o NavHost já aplica entre telas de
    // verdade, então sem isso a troca pra "Facial cadastrada com sucesso!" cortava seco.
    Crossfade(
        targetState = uiState.cadastroConcluido,
        animationSpec = tween(durationMillis = 400),
        label = "facialCaptureContent"
    ) { cadastroConcluido ->
        if (cadastroConcluido) {
            FacialCadastradaSucesso(nomeColaborador = nomeColaborador, matricula = matricula)
            return@Crossfade
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
        // classifica presença de rosto (nenhum / mais de um / pronto). Câmera limpa, sem
        // nenhum guia visual desenhado por cima.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clipToBounds()
                .background(PolarisCameraPlaceholder)
        ) {
            FrontCameraPreview(
                modifier = Modifier.fillMaxSize(),
                onCapturaDisponivel = { capturarFrameBruto = it }
            )

            val (statusMessage, statusColor) = when {
                uiState.isScanning && isCadastro -> "Mantenha o rosto parado..." to PolarisSuccess
                uiState.isScanning -> "Verificando rosto..." to PolarisSuccess
                uiState.faceDetectionStatus == FaceDetectionStatus.SemRosto -> "Nenhum rosto detectado" to PolarisOnPrimary
                uiState.faceDetectionStatus == FaceDetectionStatus.MultiplosRostos -> "Mais de um rosto detectado" to PolarisOnPrimary
                uiState.errorMessage != null -> uiState.errorMessage to MaterialTheme.colorScheme.error
                else -> "Rosto posicionado corretamente" to PolarisSuccess
            }
            Text(
                text = statusMessage.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = statusColor,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
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
