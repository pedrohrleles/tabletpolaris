package com.polarisrh.tabletpolaris.data.remote

import com.polarisrh.tabletpolaris.data.remote.dto.AtivarTabletRequest
import com.polarisrh.tabletpolaris.data.remote.dto.AtivarTabletResponse
import com.polarisrh.tabletpolaris.data.remote.dto.ColaboradoresSyncResponse
import com.polarisrh.tabletpolaris.data.remote.dto.ConfirmarDesativacaoRequest
import com.polarisrh.tabletpolaris.data.remote.dto.ConfirmarDesativacaoResponse
import com.polarisrh.tabletpolaris.data.remote.dto.ConsultarDesativacaoResponse
import com.polarisrh.tabletpolaris.data.remote.dto.FacialCadastradaRequest
import com.polarisrh.tabletpolaris.data.remote.dto.FacialNotificacaoResponse
import com.polarisrh.tabletpolaris.data.remote.dto.FacialRemovidaRequest
import com.polarisrh.tabletpolaris.data.remote.dto.HeartbeatRequest
import com.polarisrh.tabletpolaris.data.remote.dto.HeartbeatResponse
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncRequest
import com.polarisrh.tabletpolaris.data.remote.dto.MarcacoesSyncResponse
import com.polarisrh.tabletpolaris.data.remote.dto.StatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

// Toda rota abaixo termina com "/" de propósito — o nginx do backend redireciona (308) qualquer
// chamada sem barra final pra mesma URL com barra, e isso só funciona de graça se o client HTTP
// seguir redirect automaticamente em POST (nem todo cliente faz isso por padrão). Confirmado com
// o time do web: caminhos idênticos entre dev/prod, só o domínio (ver POLARIS_API_BASE_URL) muda.
interface PolarisApiService {

    @POST("coletores/ativar/")
    suspend fun ativarTablet(@Body request: AtivarTabletRequest): Response<AtivarTabletResponse>

    @POST("coletores/{idColetor}/heartbeat/")
    suspend fun enviarHeartbeat(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>

    // 200 = segue ativo/vinculado; 401 = desativado pelo RH (corpo do erro nesse caso é
    // ErroResponse, não StatusResponse).
    @GET("coletores/{idColetor}/status/")
    suspend fun consultarStatus(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String
    ): Response<StatusResponse>

    // Paginado — segue chamando com cursor = proximo_cursor da resposta anterior até vir null.
    @GET("coletores/{idColetor}/colaboradores/")
    suspend fun listarColaboradores(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Query("desde") desde: String? = null,
        @Query("cursor") cursor: String? = null,
        @Query("limite") limite: Int? = null
    ): Response<ColaboradoresSyncResponse>

    // 201 = lote aceito (ecoa nr_sequencia_lote); 409 = sequência desalinhada (corpo do erro
    // nesse caso é MarcacoesSyncErrorResponse, com nr_ultima_sequencia_aceita).
    @POST("rep-p/dispositivos/marcacoes/")
    suspend fun enviarMarcacoes(
        @Header("Authorization") bearerToken: String,
        @Body request: MarcacoesSyncRequest
    ): Response<MarcacoesSyncResponse>

    // Avisa que uma facial foi cadastrada localmente — é o que faz o colaborador ver "Facial
    // Cadastrada" (com botão de remover) no painel web em vez de "Cadastre no Tablet".
    @POST("coletores/{idColetor}/facial-cadastrada/")
    suspend fun confirmarFacialCadastrada(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Body request: FacialCadastradaRequest
    ): Response<FacialNotificacaoResponse>

    // Confirma que a facial foi de fato apagada localmente (a partir de um dt_reset_facial) —
    // é o que faz o colaborador ver "Facial Removida" no painel web.
    @POST("coletores/{idColetor}/facial-removida/")
    suspend fun confirmarFacialRemovida(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Body request: FacialRemovidaRequest
    ): Response<FacialNotificacaoResponse>

    // Consultada uma vez no startup (antes de abrir a tela de ponto) — cobre reboot sem nunca
    // ter recebido o aviso de desativação pelos outros três canais.
    @GET("coletores/{idColetor}/desativacao/")
    suspend fun consultarDesativacao(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String
    ): Response<ConsultarDesativacaoResponse>

    // Só chamada quando a fila local já está confirmada vazia — ver DesativacaoHandler.
    @POST("coletores/{idColetor}/desativacao/confirmar/")
    suspend fun confirmarDesativacao(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Body request: ConfirmarDesativacaoRequest = ConfirmarDesativacaoRequest()
    ): Response<ConfirmarDesativacaoResponse>
}
