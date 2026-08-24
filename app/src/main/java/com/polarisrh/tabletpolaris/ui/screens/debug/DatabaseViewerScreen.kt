package com.polarisrh.tabletpolaris.ui.screens.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.polarisrh.tabletpolaris.data.local.db.BatidaPendenteDao
import com.polarisrh.tabletpolaris.data.local.db.BatidaPendenteEntity
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorEntity
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoDao
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoEntity
import com.polarisrh.tabletpolaris.ui.theme.PolarisCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisError
import com.polarisrh.tabletpolaris.ui.theme.PolarisMuted
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnCard
import com.polarisrh.tabletpolaris.ui.theme.PolarisOnPrimary
import com.polarisrh.tabletpolaris.ui.theme.PolarisSuccess
import com.polarisrh.tabletpolaris.ui.theme.PolarisSurfaceDark

/** Tela temporária de debug — inspeciona o banco local direto pelo tablet, sem PC. Remover
 *  quando não for mais necessária. */
@Composable
fun DatabaseViewerScreen(
    colaboradorDao: ColaboradorDao,
    batidaPendenteDao: BatidaPendenteDao,
    tentativaReconhecimentoDao: TentativaReconhecimentoDao,
    onBack: () -> Unit
) {
    var colaboradores by remember { mutableStateOf<List<ColaboradorEntity>>(emptyList()) }
    var batidas by remember { mutableStateOf<List<BatidaPendenteEntity>>(emptyList()) }
    var tentativas by remember { mutableStateOf<List<TentativaReconhecimentoEntity>>(emptyList()) }

    LaunchedEffect(Unit) {
        colaboradores = colaboradorDao.listarTodos()
        batidas = batidaPendenteDao.listarPendentes()
        tentativas = tentativaReconhecimentoDao.listarTodas()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PolarisSurfaceDark)
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Banco de Dados (Debug)",
                style = MaterialTheme.typography.headlineSmall,
                color = PolarisOnPrimary
            )
            TextButton(onClick = onBack) {
                Text("Voltar", color = PolarisOnPrimary)
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    text = "rep_core_biometria_facial (${colaboradores.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(colaboradores) { colaborador ->
                TabelaLinha {
                    Text(
                        text = "${colaborador.matricula} — ${colaborador.nome}",
                        color = PolarisOnCard,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "CPF: ${colaborador.cpf} · ativo: ${colaborador.ativo} · " +
                            "facial cadastrada: ${if (colaborador.embeddingFacial != null) "Sim" else "Não"}",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "atualizado_em: ${colaborador.atualizadoEm}",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (colaboradores.isEmpty()) {
                item {
                    Text(
                        text = "Nenhum colaborador no cache local.",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                Text(
                    text = "batida_pendente (${batidas.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            items(batidas) { batida ->
                TabelaLinha {
                    Text(
                        text = "#${batida.id} — ${batida.matricula}",
                        color = PolarisOnCard,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "dtHora: ${batida.dtHora}",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (batidas.isEmpty()) {
                item {
                    Text(
                        text = "Nenhuma batida pendente.",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            item {
                Text(
                    text = "rep_aud_biometria_log (${tentativas.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            items(tentativas) { tentativa ->
                TabelaLinha {
                    Text(
                        text = "${tentativa.matricula} — similaridade ${"%.3f".format(tentativa.similaridadeCalculada)} " +
                            "(limiar ${"%.2f".format(tentativa.limiarAplicado)})",
                        color = if (tentativa.sucesso) PolarisSuccess else PolarisError,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "sucesso: ${tentativa.sucesso}" +
                            (tentativa.mensagemErro?.let { " · $it" } ?: ""),
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "dtTentativa: ${tentativa.dtTentativa}",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (tentativas.isEmpty()) {
                item {
                    Text(
                        text = "Nenhuma tentativa de reconhecimento registrada ainda.",
                        color = PolarisMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun TabelaLinha(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PolarisCard, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        content()
    }
    HorizontalDivider(color = PolarisMuted.copy(alpha = 0.2f))
}
