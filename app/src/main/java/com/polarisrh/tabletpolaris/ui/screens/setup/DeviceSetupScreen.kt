package com.polarisrh.tabletpolaris.ui.screens.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.theme.PolarisBlueDeep
import com.polarisrh.tabletpolaris.ui.theme.PolarisCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary

@Composable
fun DeviceSetupScreen(
    deviceAuthRepository: DeviceAuthRepository,
    onDeviceLinked: () -> Unit
) {
    val viewModel: DeviceSetupViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DeviceSetupViewModel(deviceAuthRepository) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                })
            }
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PolarisLogoMark(size = 160.dp)

        Spacer(modifier = Modifier.height(40.dp))

        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(PolarisCard, RoundedCornerShape(32.dp))
                .padding(horizontal = 48.dp, vertical = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ativar Tablet",
                color = PolarisOnCard,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "Insira o código enviado pelo Suporte Polaris RH.",
                color = PolarisMuted,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 36.dp)
            )

            OutlinedTextField(
                value = uiState.activationCode,
                onValueChange = viewModel::onActivationCodeChanged,
                label = { Text("Código de ativação", fontWeight = FontWeight.Bold) },
                textStyle = MaterialTheme.typography.titleLarge,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = PolarisOnCard,
                    unfocusedTextColor = PolarisOnCard
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            )

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(36.dp))

            Button(
                onClick = { viewModel.activateDevice(onDeviceLinked) },
                enabled = !uiState.isLoading && uiState.activationCode.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PolarisBlueDeep,
                    contentColor = PolarisOnPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = PolarisOnPrimary,
                        modifier = Modifier.height(28.dp)
                    )
                } else {
                    Text("Ativar Tablet", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
