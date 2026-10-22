package com.busalert.shared.preferences

import com.busalert.shared.domain.AppLanguage
import kotlinx.coroutines.flow.Flow

/** Preferencias del usuario, compartidas entre el reloj y el teléfono. */
data class UserPreferences(
    val language: AppLanguage = PreferenceDefaults.LANGUAGE,
    val darkTheme: Boolean = PreferenceDefaults.DARK_THEME,
    val speakResults: Boolean = PreferenceDefaults.SPEAK_RESULTS,
)

/** Una consulta hecha por el usuario (para historial y sugerencias). */
data class QueryHistoryEntry(
    val originLabel: String?,
    val destination: String,
    val routeCodes: List<String>,
    val timestampMillis: Long,
)

object PreferenceDefaults {
    val LANGUAGE = AppLanguage.SPANISH
    const val DARK_THEME = true
    const val SPEAK_RESULTS = true

    /** Consultas que se guardan en el historial. */
    const val MAX_HISTORY_ENTRIES = 20

    /** Lugares recientes que se ofrecen como sugerencia al escribir. */
    const val MAX_RECENT_PLACES = 6
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    val history: Flow<List<QueryHistoryEntry>>

    suspend fun setLanguage(language: AppLanguage)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setSpeakResults(enabled: Boolean)

    /** Agrega al inicio del historial y recorta al máximo configurado. */
    suspend fun addToHistory(entry: QueryHistoryEntry)
    suspend fun clearHistory()

    /** Reemplaza todo (por ejemplo, al sincronizar desde otro dispositivo). */
    suspend fun replaceAll(preferences: UserPreferences, history: List<QueryHistoryEntry>)
}

/** Lugares recientes sin repetir (destinos y orígenes dichos o escritos). */
fun List<QueryHistoryEntry>.recentPlaces(limit: Int = PreferenceDefaults.MAX_RECENT_PLACES): List<String> =
    flatMap { listOfNotNull(it.destination, it.originLabel) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(limit)
