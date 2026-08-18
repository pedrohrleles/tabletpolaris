package com.polarisrh.tabletpolaris.ui.screens.facial

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted

@Composable
fun FacialCapturePlaceholderScreen(
    matricula: String,
    punchRepository: PunchRepository,
    onPunchRegistered: () -> Unit,
    onCancel: () -> Unit
) {
    val viewModel: FacialCaptureViewModel = viewModel(
        factory = viewModelFactory {
            initializer { FacialCaptureViewModel(punchRepository) }
        }
    )
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Reconhecimento Facial", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Matrícula: $matricula",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )

        Box(
            modifier = Modifier
                .size(280.dp)
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Câmera será integrada aqui\nem uma próxima fase",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted
            )
        }

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.confirmPunch(matricula, onPunchRegistered) },
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Simular reconhecimento")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = onCancel, enabled = !uiState.isLoading) {
            Text("Cancelar")
        }
    }
}
