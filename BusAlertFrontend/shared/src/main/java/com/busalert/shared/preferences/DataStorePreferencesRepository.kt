package com.busalert.shared.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.sync.SyncCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persistencia de preferencias, historial y favoritos con Jetpack DataStore. */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
    private val clock: () -> Long = System::currentTimeMillis,
) : PreferencesRepository {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SPEAK_RESULTS = booleanPreferencesKey("speak_results")
        val HISTORY = stringPreferencesKey("history_json")
        val FAVORITES = stringPreferencesKey("favorites_json")
        val UPDATED_AT = longPreferencesKey("updated_at")
    }

    /** Si el archivo está dañado se usan los valores por defecto en vez de cerrar la app. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val preferences: Flow<UserPreferences> = safeData.map { it.toUserPreferences() }
    override val history: Flow<List<QueryHistoryEntry>> = safeData.map { SyncCodec.decodeHistory(it[Keys.HISTORY]) }
    override val favorites: Flow<List<Favorite>> = safeData.map { SyncCodec.decodeFavorites(it[Keys.FAVORITES]) }

    private fun Preferences.toUserPreferences() = UserPreferences(
        language = AppLanguage.entries.firstOrNull { it.code == this[Keys.LANGUAGE] } ?: PreferenceDefaults.LANGUAGE,
        darkTheme = this[Keys.DARK_THEME] ?: PreferenceDefaults.DARK_THEME,
        speakResults = this[Keys.SPEAK_RESULTS] ?: PreferenceDefaults.SPEAK_RESULTS,
    )

    /** Toda edición local marca la hora del cambio (para resolver conflictos al sincronizar). */
    private suspend fun editLocal(block: (MutablePreferences) -> Unit) {
        dataStore.edit {
            block(it)
            it[Keys.UPDATED_AT] = clock()
        }
    }

    override suspend fun setLanguage(language: AppLanguage) = editLocal { it[Keys.LANGUAGE] = language.code }

    override suspend fun setDarkTheme(enabled: Boolean) = editLocal { it[Keys.DARK_THEME] = enabled }

    override suspend fun setSpeakResults(enabled: Boolean) = editLocal { it[Keys.SPEAK_RESULTS] = enabled }

    override suspend fun addToHistory(entry: QueryHistoryEntry) = editLocal { prefs ->
        val updated = (listOf(entry) + SyncCodec.decodeHistory(prefs[Keys.HISTORY]))
            .take(PreferenceDefaults.MAX_HISTORY_ENTRIES)
        prefs[Keys.HISTORY] = SyncCodec.encodeHistory(updated)
    }

    override suspend fun clearHistory() = editLocal { it.remove(Keys.HISTORY) }

    override suspend fun toggleFavorite(favorite: Favorite) = editLocal { prefs ->
        val current = SyncCodec.decodeFavorites(prefs[Keys.FAVORITES])
        val exists = current.any { it.kind == favorite.kind && it.id == favorite.id }
        val updated = if (exists) {
            current.filterNot { it.kind == favorite.kind && it.id == favorite.id }
        } else {
            (listOf(favorite) + current).take(PreferenceDefaults.MAX_FAVORITES)
        }
        prefs[Keys.FAVORITES] = SyncCodec.encodeFavorites(updated)
    }

    override suspend fun snapshot(): SyncSnapshot {
        val p = safeData.first()
        return SyncSnapshot(
            preferences = p.toUserPreferences(),
            history = SyncCodec.decodeHistory(p[Keys.HISTORY]),
            favorites = SyncCodec.decodeFavorites(p[Keys.FAVORITES]),
            updatedAtMillis = p[Keys.UPDATED_AT] ?: 0L,
        )
    }

    override suspend fun applySnapshot(snapshot: SyncSnapshot): Boolean {
        var applied = false
        dataStore.edit {
            if (snapshot.updatedAtMillis <= (it[Keys.UPDATED_AT] ?: 0L)) return@edit
            it[Keys.LANGUAGE] = snapshot.preferences.language.code
            it[Keys.DARK_THEME] = snapshot.preferences.darkTheme
            it[Keys.SPEAK_RESULTS] = snapshot.preferences.speakResults
            it[Keys.HISTORY] = SyncCodec.encodeHistory(snapshot.history.take(PreferenceDefaults.MAX_HISTORY_ENTRIES))
            it[Keys.FAVORITES] = SyncCodec.encodeFavorites(snapshot.favorites.take(PreferenceDefaults.MAX_FAVORITES))
            // Se conserva la hora del cambio remoto: así el otro dispositivo no lo vuelve a aplicar.
            it[Keys.UPDATED_AT] = snapshot.updatedAtMillis
            applied = true
        }
        return applied
    }
}
