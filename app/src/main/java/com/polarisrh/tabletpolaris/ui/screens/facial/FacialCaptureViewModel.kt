package com.polarisrh.tabletpolaris.ui.screens.facial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusResult
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SCAN_STEPS = 40
private const val SCAN_STEP_DELAY_MS = 45L

/** RECONHECIMENTO = colaborador já tem embedding, só bate o ponto. CADASTRO = primeira vez —
 *  grava o embedding (hoje ainda falso) antes de completar a batida. */
enum class ModoCaptura { RECONHECIMENTO, CADASTRO }

data class FacialCaptureUiState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val errorMessage: String? = null
)

class FacialCaptureViewModel(
    private val modo: ModoCaptura,
    private val punchRepository: PunchRepository,
    private val colaboradorDao: ColaboradorDao,
    private val deviceStatusChecker: DeviceStatusChecker,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacialCaptureUiState())
    val uiState: StateFlow<FacialCaptureUiState> = _uiState

    /** Drives the "reading your face" progress bar; the real scan/match/embedding logic
     *  (geração de verdade do embedding, comparação facial) arrives later. */
    fun startScan(matricula: String, onSuccess: (PunchResult) -> Unit) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, errorMessage = null) }

            repeat(SCAN_STEPS) { step ->
                delay(SCAN_STEP_DELAY_MS)
                _uiState.update { it.copy(scanProgress = (step + 1f) / SCAN_STEPS) }
            }

            // Se online, confirma que o dispositivo ainda está autorizado antes de registrar a
            // batida — se foi desativado nesse meio-tempo, a navegação global já vai redirecionar
            // pra tela de ativação assim que o status revogado for detectado, então só aborta
            // aqui em vez de seguir registrando. Offline, segue direto (registra normalmente).
            if (networkMonitor.isOnline.value) {
                val status = deviceStatusChecker.checkNow()
                if (status == DeviceStatusResult.Revoked) {
                    _uiState.update { it.copy(isScanning = false) }
                    return@launch
                }
            }

            if (modo == ModoCaptura.CADASTRO) {
                // Placeholder — a geração real do embedding entra numa fase futura.
                colaboradorDao.salvarEmbedding(matricula, ByteArray(0))
            }

            val result = punchRepository.registerPunch(matricula)
            result.onSuccess { punchResult ->
                onSuccess(punchResult)
            }.onFailure { error ->
                _uiState.update { it.copy(isScanning = false, errorMessage = error.message) }
            }
        }
    }
}
