package com.polarisrh.tabletpolaris.ui.screens.clockin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STATUS_POLL_INTERVAL_MS = 30_000L

/**
 * Sem estado de UI próprio — existe só pra manter viva, enquanto a tela de ponto estiver
 * aberta, a checagem periódica de status (30s) e a checagem imediata quando a rede volta.
 * Some junto com a tela (viewModelScope é cancelado), então parar de bater ponto = parar de
 * pollar; é intencional, o heartbeat de 15min continua cobrindo o app em background.
 */
class ClockInViewModel(
    private val deviceStatusChecker: DeviceStatusChecker,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    init {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) deviceStatusChecker.checkNow()
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(STATUS_POLL_INTERVAL_MS)
                if (networkMonitor.isOnline.value) {
                    deviceStatusChecker.checkNow()
                }
            }
        }
    }
}
