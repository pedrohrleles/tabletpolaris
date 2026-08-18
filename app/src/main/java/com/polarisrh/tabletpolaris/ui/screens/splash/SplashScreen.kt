package com.polarisrh.tabletpolaris.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark

@Composable
fun SplashScreen(
    deviceAuthRepository: DeviceAuthRepository,
    onDeviceProvisioned: () -> Unit,
    onDeviceNotProvisioned: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (deviceAuthRepository.hasStoredCredentials()) {
            onDeviceProvisioned()
        } else {
            onDeviceNotProvisioned()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PolarisLogoMark(size = 180.dp)
        Text(
            text = "Polaris Ponto",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 28.dp)
        )
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 36.dp)
                .size(36.dp),
            strokeWidth = 4.dp
        )
    }
}
