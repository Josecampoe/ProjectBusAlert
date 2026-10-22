package com.busalert.wear

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.busalert.shared.config.SharedConfig
import com.busalert.shared.di.SharedDependencies
import com.busalert.shared.preferences.DataStorePreferencesRepository
import com.busalert.shared.preferences.PreferencesRepository

/** Nombre del archivo de DataStore con preferencias e historial. */
private const val PREFERENCES_STORE = "user_preferences"

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(PREFERENCES_STORE)

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

    val preferencesRepository: PreferencesRepository by lazy {
        DataStorePreferencesRepository(userPreferencesStore)
    }
}
