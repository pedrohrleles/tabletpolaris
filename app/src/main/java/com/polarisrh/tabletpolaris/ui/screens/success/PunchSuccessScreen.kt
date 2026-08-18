package com.polarisrh.tabletpolaris.ui.screens.success

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val AUTO_RETURN_DELAY_MS = 4000L

@Composable
fun PunchSuccessScreen(
    matricula: String,
    onTimeout: () -> Unit
) {
    LaunchedEffect(matricula) {
        delay(AUTO_RETURN_DELAY_MS)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Ponto registrado!", style = MaterialTheme.typography.headlineLarge)
        Text(
            text = "Matrícula $matricula",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
