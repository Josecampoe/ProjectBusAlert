package com.busalert.wear

import android.app.Application
import com.busalert.shared.config.SharedConfig
import com.busalert.shared.di.SharedDependencies

/** Punto único donde se arman las dependencias de la app del reloj. */
class BusAlertWearApp : Application() {

    val dependencies: SharedDependencies by lazy {
        SharedDependencies(
            SharedConfig(
                backendBaseUrl = BuildConfig.BACKEND_BASE_URL,
                apiKey = BuildConfig.BACKEND_API_KEY,
                useMockBackend = BuildConfig.USE_MOCK_BACKEND,
            ),
        )
    }
}
