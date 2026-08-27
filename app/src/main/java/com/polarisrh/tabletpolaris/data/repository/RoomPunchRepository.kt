package com.polarisrh.tabletpolaris.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.DeviceKeyManager
import com.polarisrh.tabletpolaris.data.local.db.BatidaDao
import com.polarisrh.tabletpolaris.data.local.db.BatidaEntity
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.work.PunchSyncWorker
import java.time.Instant
import java.util.UUID

/**
 * Sempre grava local primeiro e devolve sucesso na hora — nunca espera rede. A batida entra na
 * fila offline (`batidas_sincronizadas`, `fl_sincronizado = 0`) e um [PunchSyncWorker] é
 * disparado em segundo plano pra tentar sincronizar; se não tiver internet, o `NetworkType
 * .CONNECTED` do worker simplesmente segura a tentativa até a rede voltar, sem travar essa
 * tela. Ver diretriz do usuário: "o ponto SEMPRE será enviado para a fila offline... nós não
 * podemos travar na página Bater Ponto esperando ele sincronizar com o online."
 */
class RoomPunchRepository(
    private val context: Context,
    private val batidaDao: BatidaDao,
    private val colaboradorDao: ColaboradorDao,
    private val credentialsStore: DeviceCredentialsStore,
    private val deviceKeyManager: DeviceKeyManager = DeviceKeyManager()
) : PunchRepository {

    override suspend fun registerPunch(matricula: String, nrScore: Float, nrThreshold: Float): Result<PunchResult> {
        // O backend faz trim() em id_local/nr_matricula/dt_hr_marcacao antes de reconferir a
        // assinatura — precisa ser o MESMO valor (já trimado) usado pra assinar e pro payload,
        // senão espaço na ponta invalida a assinatura.
        val matriculaLimpa = matricula.trim()
        if (matriculaLimpa.isBlank()) {
            return Result.failure(IllegalArgumentException("Matrícula inválida"))
        }
        val credentials = credentialsStore.read()
            ?: return Result.failure(IllegalStateException("Dispositivo não ativado"))

        // Camada extra de defesa: a tela de "Bater Ponto" já bloqueia teclado/Confirmar assim
        // que a desativação é vista, mas alguém que já estava no meio do reconhecimento facial
        // nesse instante ainda chegaria até aqui — descarta sem gravar, por instrução
        // explícita ("não é por nossa conta"). Ver DesativacaoHandler.
        if (credentialsStore.estaBloqueadoPorDesativacao()) {
            return Result.failure(IllegalStateException("Tablet desativado"))
        }

        val idLocal = UUID.randomUUID().toString()
        val instanteRegistro = Instant.now()
        val dtHrMarcacao = instanteRegistro.toString()
        // Payload de assinatura confirmado com o backend: quatro valores, join com "|", sem
        // espaços — ver DeviceKeyManager.assinar().
        val assinatura = deviceKeyManager.assinar(
            "${credentials.idColetor}|$idLocal|$matriculaLimpa|$dtHrMarcacao"
        )
        val cpf = colaboradorDao.buscarPorMatricula(matriculaLimpa)?.cpf

        batidaDao.inserir(
            BatidaEntity(
                idEmpregador = credentials.idEmpregador,
                matricula = matriculaLimpa,
                cpfEmpregado = cpf,
                dtHrMarcacao = dtHrMarcacao,
                idLocal = idLocal,
                assinatura = assinatura,
                nrScore = nrScore,
                nrThreshold = nrThreshold
            )
        )

        dispararSincronizacaoImediata()

        return Result.success(PunchResult(matriculaLimpa, instanteRegistro))
    }

    /** Enfileira uma tentativa imediata (substitui qualquer tentativa ainda não iniciada) — o
     *  worker só roda de verdade quando `NetworkType.CONNECTED` for satisfeito, e sempre relê
     *  TODAS as pendentes do banco na hora de rodar, então não perde batidas concorrentes. O
     *  worker periódico (ver PolarisApplication) cobre o caso de rede que volta sem nenhuma
     *  batida nova acontecer nesse meio-tempo. */
    private fun dispararSincronizacaoImediata() {
        val request = OneTimeWorkRequestBuilder<PunchSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            PunchSyncWorker.IMMEDIATE_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}
