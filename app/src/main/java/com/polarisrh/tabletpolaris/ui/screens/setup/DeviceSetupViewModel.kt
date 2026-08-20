package com.polarisrh.tabletpolaris.ui.screens.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val ACTIVATION_CODE_LENGTH = 12

data class DeviceSetupUiState(
    val activationCode: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class DeviceSetupViewModel(
    private val deviceAuthRepository: DeviceAuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeviceSetupUiState())
    val uiState: StateFlow<DeviceSetupUiState> = _uiState

    fun onActivationCodeChanged(value: String) {
        val sanitized = value.filter { char -> char.isLetterOrDigit() }
            .uppercase()
            .take(ACTIVATION_CODE_LENGTH)
        _uiState.update { it.copy(activationCode = sanitized, errorMessage = null) }
    }

    fun activateDevice(onSuccess: () -> Unit) {
        val code = _uiState.value.activationCode.trim()
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val result = deviceAuthRepository.activateDevice(code)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false) }
                onSuccess()
            }.onFailure { error ->
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message) }
            }
        }
    }
}
