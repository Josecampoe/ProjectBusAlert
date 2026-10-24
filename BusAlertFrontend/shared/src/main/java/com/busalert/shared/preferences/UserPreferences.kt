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

enum class FavoriteKind { ROUTE, STOP }

/** Ruta o parada marcada como favorita. [id] es el código de ruta o el nombre de la parada. */
data class Favorite(
    val kind: FavoriteKind,
    val id: String,
    val label: String,
)

/**
 * Todo lo que se sincroniza entre dispositivos. [updatedAtMillis] es la hora
 * del último cambio local: al sincronizar gana el más reciente.
 */
data class SyncSnapshot(
    val preferences: UserPreferences,
    val history: List<QueryHistoryEntry>,
    val favorites: List<Favorite>,
    val updatedAtMillis: Long,
)

object PreferenceDefaults {
    val LANGUAGE = AppLanguage.SPANISH
    const val DARK_THEME = true
    const val SPEAK_RESULTS = true

    /** Consultas que se guardan en el historial. */
    const val MAX_HISTORY_ENTRIES = 20

    /** Lugares recientes que se ofrecen como sugerencia al escribir. */
    const val MAX_RECENT_PLACES = 6

    /** Favoritos que se guardan. */
    const val MAX_FAVORITES = 30
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    val history: Flow<List<QueryHistoryEntry>>
    val favorites: Flow<List<Favorite>>

    suspend fun setLanguage(language: AppLanguage)
    suspend fun setDarkTheme(enabled: Boolean)
    suspend fun setSpeakResults(enabled: Boolean)

    /** Agrega al inicio del historial y recorta al máximo configurado. */
    suspend fun addToHistory(entry: QueryHistoryEntry)
    suspend fun clearHistory()

    /** Agrega el favorito si no existe; si existe, lo quita. */
    suspend fun toggleFavorite(favorite: Favorite)

    /** Estado actual completo, para enviarlo al otro dispositivo. */
    suspend fun snapshot(): SyncSnapshot

    /**
     * Aplica lo recibido de otro dispositivo solo si es más reciente que lo
     * local. Devuelve true si se aplicó.
     */
    suspend fun applySnapshot(snapshot: SyncSnapshot): Boolean
}

/** Lugares recientes sin repetir (destinos y orígenes dichos o escritos). */
fun List<QueryHistoryEntry>.recentPlaces(limit: Int = PreferenceDefaults.MAX_RECENT_PLACES): List<String> =
    flatMap { listOfNotNull(it.destination, it.originLabel) }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }
        .take(limit)
