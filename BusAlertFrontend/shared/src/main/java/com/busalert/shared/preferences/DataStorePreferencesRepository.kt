package com.busalert.shared.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.busalert.shared.domain.AppLanguage
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Persistencia de preferencias e historial con Jetpack DataStore. */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    private val gson = Gson()

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SPEAK_RESULTS = booleanPreferencesKey("speak_results")
        val HISTORY = stringPreferencesKey("history_json")
    }

    private val historyType = object : TypeToken<List<HistoryJson>>() {}.type

    /** Si el archivo está dañado se usan los valores por defecto en vez de cerrar la app. */
    private val safeData: Flow<Preferences> = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

    override val preferences: Flow<UserPreferences> = safeData.map { p ->
        UserPreferences(
            language = AppLanguage.entries.firstOrNull { it.code == p[Keys.LANGUAGE] } ?: PreferenceDefaults.LANGUAGE,
            darkTheme = p[Keys.DARK_THEME] ?: PreferenceDefaults.DARK_THEME,
            speakResults = p[Keys.SPEAK_RESULTS] ?: PreferenceDefaults.SPEAK_RESULTS,
        )
    }

    override val history: Flow<List<QueryHistoryEntry>> = safeData.map { decodeHistory(it[Keys.HISTORY]) }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    override suspend fun setSpeakResults(enabled: Boolean) {
        dataStore.edit { it[Keys.SPEAK_RESULTS] = enabled }
    }

    override suspend fun addToHistory(entry: QueryHistoryEntry) {
        dataStore.edit { prefs ->
            val updated = (listOf(entry) + decodeHistory(prefs[Keys.HISTORY]))
                .take(PreferenceDefaults.MAX_HISTORY_ENTRIES)
            prefs[Keys.HISTORY] = encodeHistory(updated)
        }
    }

    override suspend fun clearHistory() {
        dataStore.edit { it.remove(Keys.HISTORY) }
    }

    override suspend fun replaceAll(preferences: UserPreferences, history: List<QueryHistoryEntry>) {
        dataStore.edit {
            it[Keys.LANGUAGE] = preferences.language.code
            it[Keys.DARK_THEME] = preferences.darkTheme
            it[Keys.SPEAK_RESULTS] = preferences.speakResults
            it[Keys.HISTORY] = encodeHistory(history.take(PreferenceDefaults.MAX_HISTORY_ENTRIES))
        }
    }

    // Formato estable en disco, independiente de las clases de dominio.
    private data class HistoryJson(
        val originLabel: String?,
        val destination: String?,
        val routeCodes: List<String>?,
        val timestampMillis: Long?,
    )

    private fun encodeHistory(entries: List<QueryHistoryEntry>): String = gson.toJson(
        entries.map { HistoryJson(it.originLabel, it.destination, it.routeCodes, it.timestampMillis) },
    )

    private fun decodeHistory(json: String?): List<QueryHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val raw: List<HistoryJson> = try {
            gson.fromJson(json, historyType) ?: emptyList()
        } catch (_: JsonParseException) {
            emptyList()
        }
        return raw.mapNotNull { h ->
            val destination = h.destination ?: return@mapNotNull null
            QueryHistoryEntry(h.originLabel, destination, h.routeCodes.orEmpty(), h.timestampMillis ?: 0L)
        }
    }
}
