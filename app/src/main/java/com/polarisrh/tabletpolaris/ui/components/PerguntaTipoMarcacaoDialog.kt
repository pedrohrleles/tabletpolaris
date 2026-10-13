package com.polarisrh.tabletpolaris.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.polarisrh.tabletpolaris.data.repository.TipoMarcacao
import com.polarisrh.tabletpolaris.ui.theme.PolarisCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess

/** Pergunta do ponto por exceção. Não fecha por toque fora nem pelo botão voltar — só por uma
 *  das opções ou por Cancelar. */
@Composable
fun PerguntaTipoMarcacaoDialog(
    onEscolher: (String) -> Unit,
    onCancelar: () -> Unit
) {
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .background(PolarisCard, RoundedCornerShape(4.dp))
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Entrada ou Saída?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = PolarisOnCard
            )
            Text(
                text = "Hoje o seu ponto é automático. Informe se este registro é uma Entrada ou uma Saída.",
                style = MaterialTheme.typography.bodyLarge,
                color = PolarisMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 32.dp)
            )

            OpcaoTipoMarcacao(
                titulo = "Entrada",
                descricao = "Início do trabalho",
                cor = PolarisSuccess,
                icone = Icons.AutoMirrored.Filled.Login,
                onClick = { onEscolher(TipoMarcacao.ENTRADA) }
            )
            Spacer(modifier = Modifier.height(20.dp))
            OpcaoTipoMarcacao(
                titulo = "Saída",
                descricao = "Fim do trabalho",
                cor = PolarisError,
                icone = Icons.AutoMirrored.Filled.Logout,
                onClick = { onEscolher(TipoMarcacao.SAIDA) }
            )

            TextButton(onClick = onCancelar, modifier = Modifier.padding(top = 24.dp)) {
                Text("Cancelar", color = PolarisMuted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun OpcaoTipoMarcacao(
    titulo: String,
    descricao: String,
    cor: Color,
    icone: ImageVector,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = cor, contentColor = PolarisOnPrimary),
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .border(1.5.dp, PolarisOnPrimary, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icone,
                    contentDescription = null,
                    tint = PolarisOnPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.size(20.dp))
            Column {
                Text(titulo, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(descricao, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
