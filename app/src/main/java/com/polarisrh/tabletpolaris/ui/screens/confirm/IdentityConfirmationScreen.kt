package com.polarisrh.tabletpolaris.ui.screens.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.ui.components.PolarisLogoMark
import com.polarisrh.tabletpolaris.ui.components.PolarisStripedCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisBlueDeep
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary

/**
 * Aparece quando a matrícula digitada ainda não tem embedding facial cadastrado — confirma a
 * identidade antes de mandar pro cadastro, pra evitar que alguém cadastre o rosto errado numa
 * matrícula que não é a dele.
 */
@Composable
fun IdentityConfirmationScreen(
    matricula: String,
    colaboradorDao: ColaboradorDao,
    onConfirmado: () -> Unit,
    onNegado: () -> Unit
) {
    var nome by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(matricula) {
        nome = colaboradorDao.buscarPorMatricula(matricula)?.nome
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PolarisLogoMark(size = 120.dp)

        Spacer(modifier = Modifier.height(40.dp))

        PolarisStripedCard(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
        ) {
            val nomeAtual = nome
            if (nomeAtual == null) {
                CircularProgressIndicator(color = PolarisBlueDeep)
            } else {
                Text(
                    text = "Você é $nomeAtual?",
                    color = PolarisOnCard,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Essa matrícula ainda não tem cadastro facial. Confirme sua identidade antes de continuar.",
                    color = PolarisMuted,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 36.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onNegado,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PolarisOnCard),
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                    ) {
                        Text("Não", style = MaterialTheme.typography.labelLarge)
                    }

                    Button(
                        onClick = onConfirmado,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PolarisBlueDeep,
                            contentColor = PolarisOnPrimary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(76.dp)
                    ) {
                        Text("Sim", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
