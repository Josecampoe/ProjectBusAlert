package com.busalert.shared.di

import com.busalert.shared.config.NetworkDefaults
import com.busalert.shared.config.SharedConfig
import com.busalert.shared.data.DemoTransitRepository
import com.busalert.shared.data.DemoTripAssistantRepository
import com.busalert.shared.data.RemoteTransitRepository
import com.busalert.shared.data.RemoteTripAssistantRepository
import com.busalert.shared.data.UnconfiguredTransitRepository
import com.busalert.shared.data.UnconfiguredTripAssistantRepository
import com.busalert.shared.data.api.BusAlertApi
import com.busalert.shared.domain.TransitRepository
import com.busalert.shared.domain.TripAssistantRepository
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Contenedor de dependencias del módulo compartido.
 * El backend real es el predeterminado; la demo solo se usa si la app lo
 * pide explícitamente (flag de BuildConfig habilitado solo en debug).
 */
class SharedDependencies(private val config: SharedConfig) {

    val tripAssistantRepository: TripAssistantRepository by lazy {
        when {
            config.useMockBackend -> DemoTripAssistantRepository()
            !config.isBackendConfigured -> UnconfiguredTripAssistantRepository
            else -> RemoteTripAssistantRepository(api)
        }
    }

    val transitRepository: TransitRepository by lazy {
        when {
            config.useMockBackend -> DemoTransitRepository()
            !config.isBackendConfigured -> UnconfiguredTransitRepository
            else -> RemoteTransitRepository(api)
        }
    }

    private val api: BusAlertApi by lazy {
        Retrofit.Builder()
            .baseUrl(config.backendBaseUrl)
            .client(httpClient(config))
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BusAlertApi::class.java)
    }

    companion object {
        fun httpClient(config: SharedConfig): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor(apiKeyInterceptor(config.apiKey))
            .build()

        fun apiKeyInterceptor(apiKey: String) = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header(NetworkDefaults.API_KEY_HEADER, apiKey).build())
        }
    }
}
