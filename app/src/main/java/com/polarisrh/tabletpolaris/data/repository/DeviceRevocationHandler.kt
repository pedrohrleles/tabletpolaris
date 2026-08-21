package com.polarisrh.tabletpolaris.data.repository

import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore

/**
 * Único ponto que executa o "desvincular": limpa a sessão/credenciais do dispositivo.
 *
 * NÃO apaga o cache de colaboradores (roster + embeddings faciais) aqui — essa desvinculação
 * pode não ter sido uma decisão definitiva do suporte (ex.: erro de rede, token expirado), e
 * o mesmo tablet pode voltar a ser ativado pra mesma empresa minutos depois. Zerar ou preservar
 * esse cache só é decidido na próxima ativação, comparando a empresa nova com a que estava
 * salva — ver [ColaboradorSyncRepository.prepararParaAtivacao]. A fila de batidas pendentes
 * nunca é tocada em nenhum dos dois casos.
 *
 * Chamado tanto pelo heartbeat (fl_ativo=false) quanto pelo /status (401).
 */
class DeviceRevocationHandler(
    private val credentialsStore: DeviceCredentialsStore,
    private val onRevoked: (String) -> Unit
) {
    suspend fun revoke(message: String) {
        credentialsStore.clear()
        onRevoked(message)
    }
}
