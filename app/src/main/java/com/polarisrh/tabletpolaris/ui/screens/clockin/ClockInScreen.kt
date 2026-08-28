package com.polarisrh.tabletpolaris.ui.screens.clockin

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.ColaboradorSyncRepository
import com.polarisrh.tabletpolaris.data.repository.DesativacaoHandler
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.ui.components.NumericKeypad
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.components.pressScale
import com.polarisrh.tabletpolaris.ui.theme.PolarisBlueDeep
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
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
    desativacaoHandler: DesativacaoHandler,
    onReconhecerFacial: (String) -> Unit,
    onPrecisarConfirmarIdentidade: (String) -> Unit
) {
    val viewModel: ClockInViewModel = viewModel(
        factory = viewModelFactory {
            initializer { ClockInViewModel(deviceStatusChecker, colaboradorSyncRepository, networkMonitor, colaboradorDao, desativacaoHandler) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    var matricula by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // Cabeçalho: logo no canto + relógio como tipografia limpa, sem card/borda (o visual
        // "cartão com ícone" anterior lembrava demais um placeholder genérico de IA). Fundo num
        // azul-marinho mais profundo (mesma cor já usada em botões de outras telas) — sem isso,
        // cabeçalho e corpo ficavam no mesmo tom de azul/fundo, tudo parecendo uma coisa só.
        // Usa o timezone do local de trabalho registrado na ativação (não o fuso do sistema do
        // tablet) — lido uma vez, não muda enquanto essa tela estiver aberta.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PolarisBlueDeep)
                .padding(horizontal = 48.dp, vertical = 32.dp)
        ) {
            val timezoneLocal = remember { credentialsStore.read()?.timezone }
            HorarioAtualHeader(
                timezone = timezoneLocal,
                mensagemDesativado = uiState.mensagemDesativado,
                modifier = Modifier.align(Alignment.Center)
            )

            // CenterEnd (não TopEnd) pra ficar alinhado verticalmente com o bloco de duas linhas
            // do relógio ao lado, não só grudado no topo da caixa.
            PolarisLogoMark(
                size = 56.dp,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Corpo: tudo que envolve bater o ponto forma UM bloco só. Alinhado ao TOPO (não mais
        // centralizado no espaço todo) com um respiro pequeno — centralizar deixava um vão
        // vazio grande acima de "Bater Ponto" quando o teclado+botão não preenchiam a tela.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 24.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
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
                },
                enabled = uiState.mensagemDesativado == null
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
                enabled = matricula.isNotEmpty() && !uiState.isVerificandoMatricula && uiState.mensagemDesativado == null,
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

/** Cabeçalho tipográfico puro — sem card, borda ou ícone. Data pequena/muted em cima, hora
 *  grande e em negrito embaixo, como um relógio de parede em vez de um widget genérico.
 *  [mensagemDesativado] não nulo = desativação em andamento (ver DesativacaoHandler) — some
 *  ao lado/embaixo do horário, só pra facilitar debug em campo. */
@Composable
private fun HorarioAtualHeader(timezone: String?, mensagemDesativado: String?, modifier: Modifier = Modifier) {
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

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = textoData,
            style = MaterialTheme.typography.bodyLarge,
            color = PolarisMuted
        )
        Text(
            text = agora.format(FORMATO_HORA),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold
        )
        if (mensagemDesativado != null) {
            Text(
                text = mensagemDesativado,
                style = MaterialTheme.typography.labelMedium,
                color = PolarisError,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
