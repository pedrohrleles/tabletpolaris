package com.polarisrh.tabletpolaris.ui.screens.facial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SCAN_STEPS = 40
private const val SCAN_STEP_DELAY_MS = 45L

data class FacialCaptureUiState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val errorMessage: String? = null
)

class FacialCaptureViewModel(
    private val punchRepository: PunchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacialCaptureUiState())
    val uiState: StateFlow<FacialCaptureUiState> = _uiState

    /** Drives the "reading your face" progress bar; the real scan/match logic arrives later. */
    fun startScan(matricula: String, onSuccess: (PunchResult) -> Unit) {
        if (_uiState.value.isScanning) return

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = 0f, errorMessage = null) }

            repeat(SCAN_STEPS) { step ->
                delay(SCAN_STEP_DELAY_MS)
                _uiState.update { it.copy(scanProgress = (step + 1f) / SCAN_STEPS) }
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
