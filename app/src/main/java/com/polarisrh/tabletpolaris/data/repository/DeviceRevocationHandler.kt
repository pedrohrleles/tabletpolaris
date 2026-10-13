package com.polarisrh.tabletpolaris.data.repository

import android.util.Log
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore

/**
 * Único ponto que executa o "desvincular": apaga as faciais cadastradas e limpa a
 * sessão/credenciais do dispositivo.
 *
 * O roster (matrícula/cpf/nome) NÃO é apagado aqui — essa desvinculação pode não ter sido uma
 * decisão definitiva do suporte (ex.: erro de rede, token expirado), e o mesmo tablet pode
 * voltar a ser ativado pra mesma empresa minutos depois. Zerar ou preservar o roster só é
 * decidido na próxima ativação, comparando a empresa nova com a que estava salva — ver
 * [ColaboradorSyncRepository.prepararParaAtivacao]. Faciais são diferentes: o Polaris RH
 * precisa saber que elas não existem mais neste tablet (senão o painel web continua mostrando
 * "Facial Cadastrada" pra sempre), então são sempre apagadas e o backend avisado — ver
 * [ColaboradorSyncRepository.apagarTodasFaciaisEAvisar]. Chamado ANTES de limpar as
 * credenciais, porque o aviso ao backend precisa do token ainda válido. A fila de batidas
 * pendentes nunca é tocada em nenhum dos dois casos.
 *
 * Chamado pelo heartbeat (fl_ativo=false), pelo /status (401) e pelo fluxo de desativação com
 * drenagem (ver [DesativacaoHandler]).
 */
class DeviceRevocationHandler(
    private val credentialsStore: DeviceCredentialsStore,
    private val colaboradorSyncRepository: ColaboradorSyncRepository,
    private val onRevoked: (String) -> Unit
) {
    suspend fun revoke(message: String) {
        runCatching { colaboradorSyncRepository.apagarTodasFaciaisEAvisar() }
            .onFailure { Log.w(TAG, "Falha ao apagar faciais na desvinculação (melhor esforço): ${it.message}") }
        credentialsStore.clear()
        onRevoked(message)
    }

    private companion object {
        const val TAG = "DeviceRevocationHandler"
    }
}
