package com.busalert.mobile

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.busalert.mobile.ui.MobileAppDependencies
import com.busalert.mobile.ui.settings.BackendMode
import com.busalert.shared.config.SharedConfig
import com.busalert.shared.di.SharedDependencies
import com.busalert.shared.preferences.DataStorePreferencesRepository
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.sync.DataLayerSyncPublisher
import com.busalert.shared.sync.SyncListenerService
import com.busalert.shared.sync.SyncingPreferencesRepository

/** Nombre del archivo de DataStore con preferencias, historial y favoritos. */
private const val PREFERENCES_STORE = "user_preferences"

private val Context.userPreferencesStore: DataStore<Preferences> by preferencesDataStore(PREFERENCES_STORE)

/** Punto único donde se arman las dependencias de la app del teléfono. */
class BusAlertMobileApp : Application() {

    private val config by lazy {
        SharedConfig(
            backendBaseUrl = BuildConfig.BACKEND_BASE_URL,
            apiKey = BuildConfig.BACKEND_API_KEY,
            useMockBackend = BuildConfig.USE_MOCK_BACKEND,
        )
    }

    private val shared by lazy { SharedDependencies(config) }

    /** Repositorio local (lo usa el receptor de sincronización, que no debe republicar). */
    val localPreferences: PreferencesRepository by lazy { DataStorePreferencesRepository(userPreferencesStore) }

    val appDependencies: MobileAppDependencies by lazy {
        MobileAppDependencies(
            transit = shared.transitRepository,
            preferences = SyncingPreferencesRepository(localPreferences, DataLayerSyncPublisher(this)),
            mapsEnabled = BuildConfig.HAS_MAPS_KEY,
            backendMode = when {
                config.useMockBackend -> BackendMode.DEMO
                !config.isBackendConfigured -> BackendMode.MISSING
                else -> BackendMode.CONFIGURED
            },
        )
    }
}

/** Recibe preferencias, historial y favoritos desde el reloj. */
class MobileSyncListenerService : SyncListenerService() {
    override val localPreferences: PreferencesRepository
        get() = (application as BusAlertMobileApp).localPreferences
}
