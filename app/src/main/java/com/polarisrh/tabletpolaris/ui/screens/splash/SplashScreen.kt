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
import com.polarisrh.tabletpolaris.data.repository.DesativacaoHandler
import com.polarisrh.tabletpolaris.data.repository.DeviceAuthRepository
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark

@Composable
fun SplashScreen(
    deviceAuthRepository: DeviceAuthRepository,
    desativacaoHandler: DesativacaoHandler,
    onDeviceProvisioned: () -> Unit,
    onDeviceNotProvisioned: () -> Unit
) {
    LaunchedEffect(Unit) {
        if (deviceAuthRepository.hasStoredCredentials()) {
            // Cobre o tablet ter sido desligado/reiniciado sem nunca ter recebido o aviso de
            // desativação pelos outros três canais (que só rodam com o app já em execução) —
            // ver DesativacaoHandler. Não bloqueia a navegação: se falhar (sem rede agora), os
            // canais normais (status/heartbeat) pegam assim que possível.
            desativacaoHandler.verificarNoStartup()
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
