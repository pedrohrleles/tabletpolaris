package com.polarisrh.tabletpolaris.data.remote

import com.polarisrh.tabletpolaris.data.remote.dto.AtivarTabletRequest
import com.polarisrh.tabletpolaris.data.remote.dto.AtivarTabletResponse
import com.polarisrh.tabletpolaris.data.remote.dto.HeartbeatRequest
import com.polarisrh.tabletpolaris.data.remote.dto.HeartbeatResponse
import com.polarisrh.tabletpolaris.data.remote.dto.StatusResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface PolarisApiService {

    @POST("coletores/ativar")
    suspend fun ativarTablet(@Body request: AtivarTabletRequest): Response<AtivarTabletResponse>

    // Contrato provisório — ajustar assim que o backend confirmar o endpoint real de heartbeat.
    @POST("coletores/{idColetor}/heartbeat")
    suspend fun enviarHeartbeat(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String,
        @Body request: HeartbeatRequest
    ): Response<HeartbeatResponse>

    // 200 = segue ativo/vinculado; 401 = desativado pelo RH (corpo do erro nesse caso é
    // ErroResponse, não StatusResponse).
    @GET("coletores/{idColetor}/status")
    suspend fun consultarStatus(
        @Path("idColetor") idColetor: String,
        @Header("Authorization") bearerToken: String
    ): Response<StatusResponse>
}
