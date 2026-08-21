package com.polarisrh.tabletpolaris.ui.screens.facial

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.polarisrh.tabletpolaris.data.local.NetworkMonitor
import com.polarisrh.tabletpolaris.data.local.db.ColaboradorDao
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusChecker
import com.polarisrh.tabletpolaris.data.repository.DeviceStatusResult
import com.polarisrh.tabletpolaris.data.repository.PunchRepository
import com.polarisrh.tabletpolaris.data.repository.PunchResult
import com.polarisrh.tabletpolaris.facial.FaceDetectionStatus
import com.polarisrh.tabletpolaris.facial.FaceEmbeddingExtractor
import com.polarisrh.tabletpolaris.facial.LIMIAR_RECONHECIMENTO_FACIAL
import com.polarisrh.tabletpolaris.facial.paraByteArray
import com.polarisrh.tabletpolaris.facial.paraFloatArray
import com.polarisrh.tabletpolaris.facial.similaridadeCosseno
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

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

class FacialCaptureViewModel(
    private val modo: ModoCaptura,
    private val punchRepository: PunchRepository,
    private val colaboradorDao: ColaboradorDao,
    private val faceEmbeddingExtractor: FaceEmbeddingExtractor,
    private val deviceStatusChecker: DeviceStatusChecker,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(FacialCaptureUiState())
    val uiState: StateFlow<FacialCaptureUiState> = _uiState

    fun atualizarStatusDeteccao(status: FaceDetectionStatus) {
        _uiState.update { it.copy(faceDetectionStatus = status) }
    }

    /**
     * Disparado automaticamente assim que o enquadramento fica bom (sem botão manual). A barra
     * de progresso reflete etapas reais concluídas, não uma animação — cada salto corresponde
     * a um passo de verdade do pipeline (captura → embedding → checagem → resultado).
     */
    fun startScan(matricula: String, capturarFrame: () -> Bitmap?, onSuccess: (PunchResult) -> Unit) {
        if (_uiState.value.isScanning) return
        if (_uiState.value.faceDetectionStatus != FaceDetectionStatus.Pronto) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(isScanning = true, scanProgress = 0.1f, errorMessage = null, cadastroConcluido = false)
            }

            val frame = capturarFrame()
            if (frame == null) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Não foi possível capturar a imagem. Tente novamente.") }
                return@launch
            }
            _uiState.update { it.copy(scanProgress = 0.35f) }

            val embedding = withContext(Dispatchers.Default) {
                faceEmbeddingExtractor.extrair(recortarCentro(frame))
            }
            _uiState.update { it.copy(scanProgress = 0.65f) }

            if (modo == ModoCaptura.CADASTRO) {
                colaboradorDao.salvarEmbedding(matricula, embedding.paraByteArray())
                _uiState.update { it.copy(isScanning = false, scanProgress = 1f, cadastroConcluido = true) }
                return@launch
            }

            val embeddingSalvo = colaboradorDao.buscarEmbedding(matricula)
            val similaridade = embeddingSalvo?.let { similaridadeCosseno(embedding, it.paraFloatArray()) } ?: -1f
            Log.d(TAG, "Reconhecimento — matrícula=$matricula similaridade=$similaridade limiar=$LIMIAR_RECONHECIMENTO_FACIAL")

            if (similaridade < LIMIAR_RECONHECIMENTO_FACIAL) {
                _uiState.update { it.copy(isScanning = false, errorMessage = "Rosto não reconhecido. Tente novamente.") }
                return@launch
            }

            // Se online, confirma que o dispositivo ainda está autorizado antes de registrar a
            // batida — se foi desativado nesse meio-tempo, a navegação global já vai redirecionar
            // pra tela de ativação assim que o status revogado for detectado, então só aborta
            // aqui em vez de seguir registrando. Offline, segue direto (registra normalmente).
            if (networkMonitor.isOnline.value) {
                _uiState.update { it.copy(scanProgress = 0.8f) }
                val status = deviceStatusChecker.checkNow()
                if (status == DeviceStatusResult.Revoked) {
                    _uiState.update { it.copy(isScanning = false) }
                    return@launch
                }
            }
            _uiState.update { it.copy(scanProgress = 0.95f) }

            val result = punchRepository.registerPunch(matricula)
            result.onSuccess { punchResult ->
                _uiState.update { it.copy(scanProgress = 1f) }
                onSuccess(punchResult)
            }.onFailure { error ->
                _uiState.update { it.copy(isScanning = false, errorMessage = error.message) }
            }
        }
    }

    /** Menu de debug temporário — remove o embedding cadastrado, forçando o recadastro na
     *  próxima vez que essa matrícula for digitada. */
    fun removerFacial(matricula: String, aoRemover: () -> Unit) {
        viewModelScope.launch {
            colaboradorDao.removerEmbedding(matricula)
            aoRemover()
        }
    }

    /** O modelo espera um rosto ocupando a maior parte da imagem — recorta um quadrado
     *  centralizado do frame (o mesmo enquadramento que a checagem de centralização já
     *  garante) em vez de mandar o preview inteiro, com fundo e tudo. */
    private fun recortarCentro(bitmap: Bitmap): Bitmap {
        val lado = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - lado) / 2
        val y = (bitmap.height - lado) / 2
        return Bitmap.createBitmap(bitmap, x, y, lado, lado)
    }

    private companion object {
        const val TAG = "FacialCapture"
    }
}
