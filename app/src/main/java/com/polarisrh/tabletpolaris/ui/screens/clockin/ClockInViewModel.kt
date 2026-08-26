package com.polarisrh.tabletpolaris.ui.screens.clockin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.ColaboradorSyncRepository
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val STATUS_POLL_INTERVAL_MS = 30_000L

data class ClockInUiState(
    val isVerificandoMatricula: Boolean = false,
    val erro: String? = null
)

class ClockInViewModel(
    private val deviceStatusChecker: DeviceStatusChecker,
    private val colaboradorSyncRepository: ColaboradorSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val colaboradorDao: ColaboradorDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(ClockInUiState())
    val uiState: StateFlow<ClockInUiState> = _uiState

    init {
        // Enquanto a tela de ponto estiver aberta: reage a quedas/retomadas de rede
        // verificando o status na hora que a rede volta, e mantém um polling de 30s
        // enquanto estiver online — assim uma desativação é percebida quase na hora,
        // sem depender só do heartbeat de 15min. A sincronização de colaboradores roda
        // junto, sempre uma carga completa (ver ColaboradorSyncRepository.sincronizarTudo) —
        // não depende de nenhum campo do backend indicar "mudou algo".
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                if (online) {
                    deviceStatusChecker.checkNow()
                    colaboradorSyncRepository.sincronizarTudo()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(STATUS_POLL_INTERVAL_MS)
                if (networkMonitor.isOnline.value) {
                    deviceStatusChecker.checkNow()
                    colaboradorSyncRepository.sincronizarTudo()
                }
            }
        }
    }

    /**
     * Busca a matrícula no roster local e decide o próximo passo: quem já tem embedding vai
     * direto pro reconhecimento; quem não tem passa antes pela confirmação de identidade
     * ("Você é o João?"), pra evitar que alguém cadastre o rosto errado numa matrícula alheia.
     */
    fun confirmarMatricula(
        matricula: String,
        aoReconhecerFacial: (String) -> Unit,
        aoPrecisarConfirmarIdentidade: (String) -> Unit
    ) {
        if (_uiState.value.isVerificandoMatricula) return

        viewModelScope.launch {
            _uiState.update { it.copy(isVerificandoMatricula = true, erro = null) }
            val colaborador = colaboradorDao.buscarPorMatricula(matricula)
            _uiState.update { it.copy(isVerificandoMatricula = false) }

            when {
                colaborador == null -> _uiState.update { it.copy(erro = "Matrícula não encontrada") }
                !colaborador.ativo -> _uiState.update { it.copy(erro = "Colaborador inativo") }
                colaborador.embeddingFacial != null -> aoReconhecerFacial(matricula)
                else -> aoPrecisarConfirmarIdentidade(matricula)
            }
        }
    }
}
