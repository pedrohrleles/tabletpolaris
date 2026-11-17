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
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs

/** `Instant.toString()` omite dígitos de milissegundo quando são zero à direita (ex.:
 *  "...42.500Z" vira "...42.5Z", e "...42.000Z" vira "...42Z", sem fração nenhuma) — largura
 *  variável que pode quebrar a ordenação `ORDER BY dt_hr_marcacao ASC` do SQLite, que compara a
 *  coluna como texto puro, não como data. Formatando sempre com 3 dígitos fixos, a comparação
 *  textual vira equivalente à cronológica em qualquer caso (pedido do Polaris RH: fila
 *  offline sincronizar na ordem certa). Continua um ISO-8601 válido, então nada que já faz
 *  `Instant.parse()` nesse campo precisa mudar. */
private val FORMATO_DT_HR_MARCACAO: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

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

    override suspend fun registerPunch(
        matricula: String,
        nrScore: Float,
        nrThreshold: Float,
        tpMarcacao: String?
    ): Result<PunchResult> {
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

        val instanteRegistro = Instant.now()

        // Bloqueio de toque duplo (pedido do Polaris RH: duas marcações no mesmo segundo pelo
        // mesmo colaborador, offline, viram DOIS registros no AFD que não podem ser apagados —
        // a Portaria 671 não permite exclusão depois de gerado). Checagem 100% local (banco),
        // funciona sem rede nenhuma, que é justamente onde os casos reais aconteceram. Mesmo
        // intervalo (5s) já usado pela batida via web.
        batidaDao.buscarUltimaBatida(matriculaLimpa)?.let { ultima ->
            val gapMs = abs(Duration.between(Instant.parse(ultima.dtHrMarcacao), instanteRegistro).toMillis())
            if (gapMs < COOLDOWN_MESMA_MATRICULA_MS) {
                return Result.failure(IllegalStateException("Ponto já registrado"))
            }
        }

        val idLocal = UUID.randomUUID().toString()
        val dtHrMarcacao = FORMATO_DT_HR_MARCACAO.format(instanteRegistro)
        // Payload de assinatura: quatro valores, join com "|", sem espaços — o tipo de marcação
        // (ponto por exceção) só entra como quinto valor quando for informado.
        val campos = listOfNotNull(credentials.idColetor, idLocal, matriculaLimpa, dtHrMarcacao, tpMarcacao)
        val assinatura = deviceKeyManager.assinar(campos.joinToString("|"))
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
                nrThreshold = nrThreshold,
                tpMarcacao = tpMarcacao
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

    private companion object {
        const val COOLDOWN_MESMA_MATRICULA_MS = 5_000L
    }
}
