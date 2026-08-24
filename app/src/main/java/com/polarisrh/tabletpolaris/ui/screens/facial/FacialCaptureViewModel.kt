package com.polarisrh.tabletpolaris.ui.screens.facial

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.local.DeviceCredentialsStore
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoDao
import com.polarisrh.tabletpolaris.data.local.db.TentativaReconhecimentoEntity
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusResult
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import com.polarisrh.tabletpolaris.facial.FaceDetectionStatus
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
import com.polarisrh.tabletpolaris.facial.FacePositionChecker
import com.polarisrh.tabletpolaris.facial.LIMIAR_RECONHECIMENTO_FACIAL
import com.polarisrh.tabletpolaris.facial.paraByteArray
import com.polarisrh.tabletpolaris.facial.paraFloatArray
import com.polarisrh.tabletpolaris.facial.similaridadeCosseno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.sqrt

/** RECONHECIMENTO = colaborador já tem embedding, compara antes de bater o ponto. CADASTRO =
 *  primeira vez — gera e grava o embedding, sem bater ponto nenhum (isso acontece depois,
 *  no reconhecimento, pra manter os dois fluxos separados). */
enum class ModoCaptura { RECONHECIMENTO, CADASTRO }

data class FacialCaptureUiState(
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val errorMessage: String? = null,
    val cadastroConcluido: Boolean = false,
    val faceDetectionStatus: FaceDetectionStatus = FaceDetectionStatus.SemRosto
)

/** Intervalo do monitoramento contínuo de enquadramento (roda direto no PreviewView.bitmap). */
private const val INTERVALO_MONITORAMENTO_MS = 150L

/** Pollings seguidos com o MESMO status novo até ele realmente trocar na tela — evita
 *  "piscar" entre dois estados por causa de ruído de uma leitura isolada. */
private const val POLLS_PARA_TROCAR_STATUS = 2

/** Cadastro precisa ficar parado esse tempo com o rosto bem enquadrado — colhe várias
 *  amostras nesse período em vez de confiar num único frame instantâneo. */
private const val DURACAO_CADASTRO_MS = 3000L
private const val INTERVALO_AMOSTRA_MS = 750L

/** Reconhecimento também colhe amostras por um tempo antes de decidir — sem isso, a checagem
 *  é tão rápida que o resultado parece aparecer "antes" de qualquer verificação de verdade.
 *  Duração e intervalo reduzidos (mantendo as mesmas ~4 amostras de antes, só num intervalo de
 *  tempo mais curto) pra bater ponto mais rápido sem colher menos amostra pra média. */
private const val DURACAO_RECONHECIMENTO_MS = 1400L
private const val INTERVALO_AMOSTRA_RECONHECIMENTO_MS = 350L

/** Espera depois de uma falha de reconhecimento antes de deixar tentar de novo sozinho — sem
 *  isso, com o rosto ainda no enquadramento, ficava tentando e falhando em loop imediato. */
private const val COOLDOWN_APOS_FALHA_MS = 1800L

class FacialCaptureViewModel(
    private val matricula: String,
    private val modo: ModoCaptura,
    private val punchRepository: PunchRepository,
    private val colaboradorDao: ColaboradorDao,
    private val tentativaReconhecimentoDao: TentativaReconhecimentoDao,
    private val credentialsStore: DeviceCredentialsStore,
    private val faceEmbeddingExtractor: FaceEmbeddingExtractor,
    private val deviceStatusChecker: DeviceStatusChecker,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacialCaptureUiState())
    val uiState: StateFlow<FacialCaptureUiState> = _uiState

    private val facePositionChecker = FacePositionChecker()

    private var capturarFrameBruto: (() -> Bitmap?)? = null
    private var calcularOvalRect: ((Bitmap) -> Rect)? = null
    private var onPunchRegistered: ((PunchResult) -> Unit)? = null

    private var jobAtivo: Job? = null
    private var emCooldown = false

    private var statusEmitido: FaceDetectionStatus = FaceDetectionStatus.SemRosto
    private var statusCandidato: FaceDetectionStatus? = null
    private var contagemCandidato = 0

    /**
     * Chamado uma vez pela tela, assim que a câmera fica disponível.
     * [capturarFrameBruto] devolve o frame inteiro exibido (usado tanto pra checar
     * enquadramento quanto como fonte do recorte pro embedding — o recorte em si é sempre
     * feito ao redor do rosto DETECTADO, não de uma área fixa da tela, ver [amostrarEmbedding]).
     * [calcularOvalRect] devolve, pra um bitmap dado, o retângulo do oval nesse mesmo bitmap.
     */
    fun iniciar(
        capturarFrameBruto: () -> Bitmap?,
        calcularOvalRect: (Bitmap) -> Rect,
        onPunchRegistered: (PunchResult) -> Unit
    ) {
        this.capturarFrameBruto = capturarFrameBruto
        this.calcularOvalRect = calcularOvalRect
        this.onPunchRegistered = onPunchRegistered
        monitorarEnquadramento()
    }

    /**
     * Roda a detecção direto no mesmo bitmap exibido/capturado (PreviewView.bitmap), não num
     * stream de análise separado — garante que "dentro do oval" seja checado exatamente
     * contra o que a pessoa vê na tela, sem descompasso de rotação/espelhamento/campo de
     * visão entre streams diferentes da câmera (era a causa real da assimetria esquerda/
     * direita e do "preciso afastar a câmera" reportados).
     *
     * Continua rodando mesmo durante o hold/verificação (isScanning=true) — é assim que a
     * gente detecta o rosto saindo do enquadramento NO MEIO do cadastro/reconhecimento e
     * cancela o progresso (ver [avaliarComDebounce]), em vez de deixar rodar até o fim com
     * amostras ruins.
     */
    private fun monitorarEnquadramento() {
        viewModelScope.launch {
            while (isActive) {
                delay(INTERVALO_MONITORAMENTO_MS)

                val bitmap = capturarFrameBruto?.invoke() ?: continue
                val ovalRect = calcularOvalRect?.invoke(bitmap) ?: continue
                val status = facePositionChecker.verificar(bitmap, ovalRect)
                avaliarComDebounce(status)
            }
        }
    }

    private fun avaliarComDebounce(statusBruto: FaceDetectionStatus) {
        if (statusBruto == statusEmitido) {
            statusCandidato = null
            contagemCandidato = 0
            return
        }

        if (statusBruto == statusCandidato) {
            contagemCandidato++
        } else {
            statusCandidato = statusBruto
            contagemCandidato = 1
        }

        if (contagemCandidato < POLLS_PARA_TROCAR_STATUS) return

        statusEmitido = statusBruto
        statusCandidato = null
        contagemCandidato = 0
        _uiState.update { it.copy(faceDetectionStatus = statusEmitido) }

        if (statusEmitido != FaceDetectionStatus.Pronto) {
            // Saiu do enquadramento bom — cancela qualquer captura/hold em andamento (cadastro
            // em progresso é abortado sem salvar nada) e zera a barra.
            jobAtivo?.cancel()
            jobAtivo = null
            _uiState.update { it.copy(isScanning = false, scanProgress = 0f) }
            return
        }

        if (_uiState.value.cadastroConcluido || emCooldown || _uiState.value.isScanning) return

        jobAtivo = viewModelScope.launch {
            if (modo == ModoCaptura.CADASTRO) cadastrarComHold() else reconhecer()
        }
    }

    /**
     * Captura um frame e gera um recorte ALINHADO pelos olhos (rotacionado/escalado pra
     * posição canônica, ver [FacePositionChecker.detectarEAlinhar]) antes de extrair o
     * embedding — sem isso, a similaridade variava com inclinação da cabeça, distância da
     * câmera e até mudança de penteado (recorte só pelo bounding box inclui cabelo/fundo de
     * forma inconsistente).
     */
    private suspend fun amostrarEmbedding(): FloatArray? {
        val frame = capturarFrameBruto?.invoke() ?: return null
        val rosto = facePositionChecker.detectarEAlinhar(frame) ?: return null
        return withContext(Dispatchers.Default) { faceEmbeddingExtractor.extrair(rosto) }
    }

    private suspend fun cadastrarComHold() {
        _uiState.update { it.copy(isScanning = true, scanProgress = 0f, errorMessage = null) }

        val embeddings = mutableListOf<FloatArray>()
        val inicio = System.currentTimeMillis()

        while (System.currentTimeMillis() - inicio < DURACAO_CADASTRO_MS) {
            val decorrido = System.currentTimeMillis() - inicio
            _uiState.update { it.copy(scanProgress = (decorrido.toFloat() / DURACAO_CADASTRO_MS).coerceIn(0f, 1f)) }

            amostrarEmbedding()?.let { embeddings.add(it) }
            delay(INTERVALO_AMOSTRA_MS)
        }

        if (embeddings.isEmpty()) {
            _uiState.update { it.copy(isScanning = false, scanProgress = 0f, errorMessage = "Não foi possível capturar o rosto. Tente novamente.") }
            return
        }

        colaboradorDao.salvarEmbedding(matricula, mediaNormalizada(embeddings).paraByteArray())
        _uiState.update { it.copy(isScanning = false, scanProgress = 1f, cadastroConcluido = true) }
    }

    /**
     * Fica ~2s colhendo amostras (igual ao cadastro) antes de decidir — só revela "reconhecido"
     * ou "não reconhecido" no final dessa janela, pra parecer (e ser) uma verificação de
     * verdade, não um veredito instantâneo.
     */
    private suspend fun reconhecer() {
        _uiState.update { it.copy(isScanning = true, scanProgress = 0f, errorMessage = null) }

        val embeddings = mutableListOf<FloatArray>()
        val inicio = System.currentTimeMillis()

        while (System.currentTimeMillis() - inicio < DURACAO_RECONHECIMENTO_MS) {
            val decorrido = System.currentTimeMillis() - inicio
            _uiState.update { it.copy(scanProgress = (decorrido.toFloat() / DURACAO_RECONHECIMENTO_MS).coerceIn(0f, 0.9f)) }

            amostrarEmbedding()?.let { embeddings.add(it) }
            delay(INTERVALO_AMOSTRA_RECONHECIMENTO_MS)
        }

        if (embeddings.isEmpty()) {
            _uiState.update { it.copy(isScanning = false, scanProgress = 0f, errorMessage = "Não foi possível capturar a imagem. Tente novamente.") }
            entrarEmCooldown()
            return
        }
        val embedding = mediaNormalizada(embeddings)
        _uiState.update { it.copy(scanProgress = 0.9f) }

        val embeddingSalvo = colaboradorDao.buscarEmbedding(matricula)?.paraFloatArray()
        val similaridade = if (embeddingSalvo != null && embeddingSalvo.size == embedding.size) {
            similaridadeCosseno(embedding, embeddingSalvo)
        } else {
            // Embedding ausente ou de um formato antigo (ex.: placeholder vazio de testes
            // anteriores ao MobileFaceNet de verdade) — trata como não reconhecido em vez
            // de estourar o array comparando tamanhos diferentes.
            if (embeddingSalvo != null) Log.w(TAG, "Embedding salvo com tamanho inesperado (${embeddingSalvo.size}) pra matrícula=$matricula — recadastro necessário.")
            -1f
        }
        Log.d(TAG, "Reconhecimento — matrícula=$matricula similaridade=$similaridade limiar=$LIMIAR_RECONHECIMENTO_FACIAL")

        val sucesso = similaridade >= LIMIAR_RECONHECIMENTO_FACIAL
        registrarTentativa(similaridade, sucesso, if (sucesso) null else "Rosto não reconhecido")

        if (!sucesso) {
            _uiState.update { it.copy(isScanning = false, scanProgress = 0f, errorMessage = "Rosto não reconhecido. Tente novamente.") }
            entrarEmCooldown()
            return
        }

        // Se online, confirma que o dispositivo ainda está autorizado antes de registrar a
        // batida — se foi desativado nesse meio-tempo, a navegação global já vai redirecionar
        // pra tela de ativação assim que o status revogado for detectado, então só aborta
        // aqui em vez de seguir registrando. Offline, segue direto (registra normalmente).
        if (networkMonitor.isOnline.value) {
            _uiState.update { it.copy(scanProgress = 0.93f) }
            val status = deviceStatusChecker.checkNow()
            if (status == DeviceStatusResult.Revoked) {
                _uiState.update { it.copy(isScanning = false, scanProgress = 0f) }
                return
            }
        }
        _uiState.update { it.copy(scanProgress = 0.97f) }

        val result = punchRepository.registerPunch(matricula)
        result.onSuccess { punchResult ->
            _uiState.update { it.copy(scanProgress = 1f) }
            onPunchRegistered?.invoke(punchResult)
        }.onFailure { error ->
            _uiState.update { it.copy(isScanning = false, scanProgress = 0f, errorMessage = error.message) }
            entrarEmCooldown()
        }
    }

    /** Espelha a rep_aud_biometria_log do web — uma linha por tentativa de reconhecimento,
     *  sucesso ou não, pra calibrar o limiar com dados reais. */
    private suspend fun registrarTentativa(similaridade: Float, sucesso: Boolean, mensagemErro: String?) {
        val idEmpregador = credentialsStore.read()?.idEmpregador ?: return
        tentativaReconhecimentoDao.inserir(
            TentativaReconhecimentoEntity(
                idEmpregador = idEmpregador,
                matricula = matricula,
                similaridadeCalculada = similaridade,
                limiarAplicado = LIMIAR_RECONHECIMENTO_FACIAL,
                sucesso = sucesso,
                mensagemErro = mensagemErro,
                dtTentativa = Instant.now().toString()
            )
        )
    }

    private fun entrarEmCooldown() {
        emCooldown = true
        viewModelScope.launch {
            delay(COOLDOWN_APOS_FALHA_MS)
            emCooldown = false
            if (_uiState.value.faceDetectionStatus == FaceDetectionStatus.Pronto && !_uiState.value.isScanning) {
                jobAtivo = viewModelScope.launch { reconhecer() }
            }
        }
    }

    /** Média dos embeddings colhidos durante o hold, renormalizada por L2 (mesma escala usada
     *  na comparação por cosseno) — reduz ruído de qualquer amostra isolada. */
    private fun mediaNormalizada(embeddings: List<FloatArray>): FloatArray {
        val tamanho = embeddings.first().size
        val soma = FloatArray(tamanho)
        for (embedding in embeddings) {
            for (i in 0 until tamanho) soma[i] += embedding[i]
        }
        for (i in 0 until tamanho) soma[i] /= embeddings.size

        var normaQuadrado = 0f
        for (v in soma) normaQuadrado += v * v
        val norma = sqrt(normaQuadrado.coerceAtLeast(1e-10f))
        return FloatArray(tamanho) { i -> soma[i] / norma }
    }

    /** Menu de debug temporário — remove o embedding cadastrado, forçando o recadastro na
     *  próxima vez que essa matrícula for digitada. */
    fun removerFacial(aoRemover: () -> Unit) {
        viewModelScope.launch {
            colaboradorDao.removerEmbedding(matricula)
            aoRemover()
        }
    }

    private companion object {
        const val TAG = "FacialCapture"
    }
}
