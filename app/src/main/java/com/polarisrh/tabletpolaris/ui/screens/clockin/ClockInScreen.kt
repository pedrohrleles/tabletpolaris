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
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Battery0Bar
import androidx.compose.material.icons.filled.Battery1Bar
import androidx.compose.material.icons.filled.Battery2Bar
import androidx.compose.material.icons.filled.Battery3Bar
import androidx.compose.material.icons.filled.Battery4Bar
import androidx.compose.material.icons.filled.Battery5Bar
import androidx.compose.material.icons.filled.Battery6Bar
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess
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

            // Painel nativo de Wi-Fi (Settings.Panel.ACTION_WIFI) — só mostra ~3 redes (limite do
            // próprio painel, renderizado pelo Settings, não customizável por nós; sem meio-termo
            // com "mostrar mais" próprio sem construir uma tela de rede dentro do app, que exigiria
            // permissão de localização — decisão de não fazer isso por ora). Sem Device Owner, o
            // Android bloqueia QUALQUER app de fora aparecer enquanto o nosso está fixado — nem o
            // painel "leve" escapa dessa regra. Então desafixamos programaticamente antes de abrir
            // (sem exigir o gesto manual de quem está usando) e a tela se refixa sozinha ao voltar,
            // graças ao startLockTask() já disparado em onWindowFocusChanged. Mostra só
            // conectado/desconectado (não o nome da rede) — mostrar o SSID exigiria permissão de
            // localização, desproporcional pra um app de bater ponto.
            val activity = LocalContext.current as Activity
            val online by networkMonitor.isOnline.collectAsState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                IconButton(onClick = {
                    activity.stopLockTask()
                    activity.startActivity(Intent(Settings.Panel.ACTION_WIFI))
                }) {
                    Icon(
                        imageVector = Icons.Filled.Wifi,
                        contentDescription = "Configurar Wi-Fi",
                        tint = PolarisOnPrimary
                    )
                }
                Text(
                    text = if (online) "Conectado" else "Desconectado",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (online) PolarisSuccess else PolarisError
                )
            }

            // CenterEnd (não TopEnd) pra ficar alinhado verticalmente com o bloco de duas linhas
            // do relógio ao lado, não só grudado no topo da caixa. Bateria fica dentro dessa
            // mesma Row, à esquerda do logo — espelhando o Wi-Fi do outro lado.
            val bateria = rememberBatteryStatus()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                val corBateria = if (bateria.percent <= 20 && !bateria.isCharging) PolarisError else PolarisOnPrimary
                Text(
                    text = "${bateria.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = corBateria
                )
                Icon(
                    imageVector = bateriaIcone(bateria.percent, bateria.isCharging),
                    contentDescription = if (bateria.isCharging) "Bateria carregando" else "Bateria",
                    tint = corBateria,
                    modifier = Modifier.padding(start = 6.dp)
                )
                Spacer(modifier = Modifier.width(36.dp))
                PolarisLogoMark(size = 56.dp)
            }
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

private data class BatteryStatus(val percent: Int, val isCharging: Boolean)

/** Observa o broadcast sticky ACTION_BATTERY_CHANGED (sem permissão nenhuma) — plugado direto
 *  na UI porque só é usado pra exibição aqui, diferente do [NetworkMonitor] que é singleton
 *  compartilhado (rede também guia decisão de sync, bateria não). EXTRA_PLUGGED em vez de
 *  BatteryManager.isCharging() pelo mesmo motivo do [com.polarisrh.tabletpolaris.data.local.DeviceTelemetryCollector]:
 *  mais confiável que o estado interno do chip de carga em vários aparelhos. */
@Composable
private fun rememberBatteryStatus(): BatteryStatus {
    val context = LocalContext.current
    var percent by remember { mutableIntStateOf(100) }
    var isCharging by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                val nivel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val escala = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (nivel >= 0 && escala > 0) {
                    percent = nivel * 100 / escala
                }
                isCharging = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }

    return BatteryStatus(percent, isCharging)
}

private fun bateriaIcone(percent: Int, isCharging: Boolean): ImageVector {
    if (isCharging) return Icons.Filled.BatteryChargingFull
    return when {
        percent >= 95 -> Icons.Filled.BatteryFull
        percent >= 80 -> Icons.Filled.Battery6Bar
        percent >= 65 -> Icons.Filled.Battery5Bar
        percent >= 50 -> Icons.Filled.Battery4Bar
        percent >= 35 -> Icons.Filled.Battery3Bar
        percent >= 20 -> Icons.Filled.Battery2Bar
        percent >= 10 -> Icons.Filled.Battery1Bar
        else -> Icons.Filled.Battery0Bar
    }
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
