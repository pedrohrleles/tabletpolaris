package com.polarisrh.tabletpolaris.data.remote

import com.polarisrh.tabletpolaris.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object PolarisApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    val service: PolarisApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.POLARIS_API_BASE_URL)
            .client(buildHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PolarisApiService::class.java)
    }

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }
}
