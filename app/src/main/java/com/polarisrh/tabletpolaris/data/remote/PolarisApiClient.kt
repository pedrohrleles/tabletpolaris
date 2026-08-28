package com.polarisrh.tabletpolaris.data.remote

import com.polarisrh.tabletpolaris.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

// Timeout curto de propósito pro caminho interativo (ex.: checagem de status antes de bater o
// ponto) — o padrão de 10s do OkHttp deixava a tela travada esperando o timeout inteiro quando a
// rede falhava. Backend agora é remoto de verdade (dev/prod), não mais LAN — reavaliar se 4s
// começar a estourar com frequência em condições normais de internet.
private const val TIMEOUT_SEGUNDOS = 4L

// POST /rep-p/dispositivos/marcacoes nunca roda no caminho interativo (só via PunchSyncWorker
// em segundo plano — ver PunchSyncRepository), então não tem o mesmo motivo pra falhar rápido.
// Com 4s, um lote com várias marcações pendentes (o processamento de cada uma no backend soma)
// estourava o timeout ANTES do servidor terminar de responder — o backend via "request aborted"
// enquanto a fila nunca saía do PENDENTE, mesmo com a rede saudável.
private const val TIMEOUT_SEGUNDOS_SYNC_LOTE = 30L

object PolarisApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    val service: PolarisApiService by lazy {
        buildRetrofit(buildHttpClient(TIMEOUT_SEGUNDOS)).create(PolarisApiService::class.java)
    }

    /** Mesmo backend, só que com timeout maior — ver [TIMEOUT_SEGUNDOS_SYNC_LOTE]. Usar só pra
     *  chamadas que rodam em segundo plano (nunca na tela de "Bater Ponto"). */
    val syncService: PolarisApiService by lazy {
        buildRetrofit(buildHttpClient(TIMEOUT_SEGUNDOS_SYNC_LOTE)).create(PolarisApiService::class.java)
    }

    private fun buildRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.POLARIS_API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    private fun buildHttpClient(timeoutSegundos: Long): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(timeoutSegundos, TimeUnit.SECONDS)
            .readTimeout(timeoutSegundos, TimeUnit.SECONDS)
            .writeTimeout(timeoutSegundos, TimeUnit.SECONDS)
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }
}
