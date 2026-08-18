package com.polarisrh.tabletpolaris.ui.screens.facial

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FacialCaptureUiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FacialCaptureViewModel(
    private val punchRepository: PunchRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacialCaptureUiState())
    val uiState: StateFlow<FacialCaptureUiState> = _uiState

    fun confirmPunch(matricula: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = punchRepository.registerPunch(matricula)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
