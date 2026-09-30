package com.polarisrh.tabletpolaris.ui.components

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.provider.Settings
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess

/** Painel nativo de Wi-Fi (Settings.Panel.ACTION_WIFI) — só mostra ~3 redes (limite do próprio
 *  painel, renderizado pelo Settings, não customizável por nós; sem meio-termo com "mostrar mais"
 *  próprio sem construir uma tela de rede dentro do app, que exigiria permissão de localização —
 *  decisão de não fazer isso por ora). Sem Device Owner, o Android bloqueia QUALQUER app de fora
 *  aparecer enquanto o nosso está fixado — nem o painel "leve" escapa dessa regra. Então
 *  desafixamos programaticamente antes de abrir (sem exigir o gesto manual de quem está usando) e
 *  a tela se refixa sozinha ao voltar, graças ao startLockTask() já disparado em
 *  onWindowFocusChanged. Mostra só conectado/desconectado (não o nome da rede) — mostrar o SSID
 *  exigiria permissão de localização, desproporcional pra um app de bater ponto. */
@Composable
fun WifiStatusIndicator(networkMonitor: NetworkMonitor, modifier: Modifier = Modifier) {
    val activity = LocalContext.current as Activity
    val online by networkMonitor.isOnline.collectAsState()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
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
}

@Composable
fun BatteryStatusIndicator(modifier: Modifier = Modifier) {
    val bateria = rememberBatteryStatus()
    val corBateria = if (bateria.percent <= 20 && !bateria.isCharging) PolarisError else PolarisOnPrimary
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
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
    }
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
