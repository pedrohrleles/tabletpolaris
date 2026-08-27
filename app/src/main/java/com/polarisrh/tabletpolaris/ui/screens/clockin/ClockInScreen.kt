package com.polarisrh.tabletpolaris.ui.screens.clockin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.ColaboradorSyncRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.ui.components.NumericKeypad
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.components.pressScale
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Safety cap only — matrícula is a growing numeric id (1, 2, 3, ...), not a fixed-length code. */
private const val MAX_MATRICULA_LENGTH = 10

/** Prefixo fixo de toda matrícula no Polaris RH (ex.: "MAT-1042") — só o número é digitado. */
private const val MATRICULA_PREFIX = "MAT-"

@Composable
fun ClockInScreen(
    deviceStatusChecker: DeviceStatusChecker,
    colaboradorSyncRepository: ColaboradorSyncRepository,
    networkMonitor: NetworkMonitor,
    colaboradorDao: ColaboradorDao,
    credentialsStore: DeviceCredentialsStore,
    onReconhecerFacial: (String) -> Unit,
    onPrecisarConfirmarIdentidade: (String) -> Unit,
    onAbrirBancoDeDados: () -> Unit,
    onSairAtivacao: () -> Unit
) {
    val viewModel: ClockInViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ClockInViewModel(deviceStatusChecker, colaboradorSyncRepository, networkMonitor, colaboradorDao) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    var matricula by remember { mutableStateOf("") }
    var showMenuDebug by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Box compartilhado (não Row) — a altura dele é definida pelo maior filho (o card do
        // horário), e os dois filhos se centralizam nessa MESMA altura via align, garantindo
        // que fiquem alinhados verticalmente entre si mesmo com tamanhos diferentes.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(48.dp)
        ) {
            // Centralizado horizontalmente, fixo perto do topo — não acompanha o deslocamento
            // pra baixo do conteúdo principal abaixo. Usa o timezone do local de trabalho
            // registrado na ativação (não o fuso do sistema do tablet) — lido uma vez, não
            // muda enquanto essa tela estiver aberta.
            val timezoneLocal = remember { credentialsStore.read()?.timezone }
            HorarioAtualCard(timezone = timezoneLocal, modifier = Modifier.align(Alignment.Center))

            // Menu temporário de debug — remover quando não for mais necessário.
            PolarisLogoMark(
                size = 64.dp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable { showMenuDebug = true }
            )
        }

        if (showMenuDebug) {
            AlertDialog(
                onDismissRequest = { showMenuDebug = false },
                title = { Text("Menu de debug") },
                text = { Text("Ferramentas temporárias — serão removidas depois.") },
                confirmButton = {
                    TextButton(onClick = {
                        showMenuDebug = false
                        onAbrirBancoDeDados()
                    }) {
                        Text("Banco de Dados")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showMenuDebug = false
                        onSairAtivacao()
                    }) {
                        Text("Sair (Ativação)")
                    }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Top maior que o resto — desloca o bloco centralizado um pouco pra baixo.
                .padding(start = 48.dp, end = 48.dp, top = 112.dp, bottom = 48.dp),
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

            AnimatedVisibility(
                visible = uiState.erro != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Text(
                    text = uiState.erro.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            val confirmarInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = {
                    viewModel.confirmarMatricula(
                        matricula = MATRICULA_PREFIX + matricula,
                        aoReconhecerFacial = onReconhecerFacial,
                        aoPrecisarConfirmarIdentidade = onPrecisarConfirmarIdentidade
                    )
                },
                enabled = matricula.isNotEmpty() && !uiState.isVerificandoMatricula,
                interactionSource = confirmarInteractionSource,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .pressScale(confirmarInteractionSource)
            ) {
                Text("Confirmar", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun MatriculaField(matricula: String) {
    // Pequeno "pop" a cada dígito digitado (ou apagado) — sem isso o número só trocava na
    // hora, sem nenhum feedback de que o toque no teclado surtiu efeito.
    val escala = remember { Animatable(1f) }
    LaunchedEffect(matricula) {
        escala.snapTo(1.08f)
        escala.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
            .padding(horizontal = 26.dp, vertical = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.scale(escala.value)
        ) {
            Text(
                text = MATRICULA_PREFIX + matricula,
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

private val FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss")

@Composable
private fun HorarioAtualCard(timezone: String?, modifier: Modifier = Modifier) {
    // Fuso do local de trabalho registrado (ex.: "America/Rio_Branco" pro Acre) — cai pro fuso
    // do próprio tablet só se o local não tiver essa informação ou o valor vier inválido.
    val zoneId = remember(timezone) {
        timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
    }

    var agora by remember(zoneId) { mutableStateOf(ZonedDateTime.now(zoneId)) }
    LaunchedEffect(zoneId) {
        while (true) {
            agora = ZonedDateTime.now(zoneId)
            delay(1000L)
        }
    }

    val diaSemana = agora.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("pt", "BR"))
    val mes = agora.month.getDisplayName(TextStyle.FULL, Locale("pt", "BR"))
    val textoData = "$diaSemana ${agora.dayOfMonth} de $mes"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .border(1.5.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .height(IntrinsicSize.Min)
    ) {
        Icon(
            imageVector = Icons.Default.Schedule,
            contentDescription = null,
            tint = PolarisMuted,
            // Acompanha a altura das duas linhas de texto ao lado, em vez de um tamanho fixo.
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = agora.format(FORMATO_HORA),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = textoData,
                style = MaterialTheme.typography.bodySmall,
                color = PolarisMuted
            )
        }
    }
}
