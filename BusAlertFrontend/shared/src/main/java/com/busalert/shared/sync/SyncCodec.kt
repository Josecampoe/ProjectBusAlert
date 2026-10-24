package com.busalert.shared.sync

import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.SyncSnapshot
import com.busalert.shared.preferences.UserPreferences
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.reflect.TypeToken

/**
 * Formato JSON estable para disco y para la Data Layer, independiente de las
 * clases de dominio (los campos anulables toleran versiones anteriores).
 */
object SyncCodec {
    private val gson = Gson()

    private data class HistoryJson(
        val originLabel: String?,
        val destination: String?,
        val routeCodes: List<String>?,
        val timestampMillis: Long?,
    )

    private data class FavoriteJson(val kind: String?, val id: String?, val label: String?)

    private data class PreferencesJson(val language: String?, val darkTheme: Boolean?, val speakResults: Boolean?)

    private data class SnapshotJson(
        val preferences: PreferencesJson?,
        val history: List<HistoryJson>?,
        val favorites: List<FavoriteJson>?,
        val updatedAtMillis: Long?,
    )

    private val historyType = object : TypeToken<List<HistoryJson>>() {}.type
    private val favoritesType = object : TypeToken<List<FavoriteJson>>() {}.type

    private fun QueryHistoryEntry.toJson() = HistoryJson(originLabel, destination, routeCodes, timestampMillis)
    private fun Favorite.toJson() = FavoriteJson(kind.name, id, label)

    private fun HistoryJson.toDomain(): QueryHistoryEntry? =
        destination?.let { QueryHistoryEntry(originLabel, it, routeCodes.orEmpty(), timestampMillis ?: 0L) }

    private fun FavoriteJson.toDomain(): Favorite? {
        val favoriteKind = FavoriteKind.entries.firstOrNull { it.name == kind } ?: return null
        val favoriteId = id ?: return null
        return Favorite(favoriteKind, favoriteId, label ?: favoriteId)
    }

    private inline fun <T> parseOrNull(block: () -> T): T? = try {
        block()
    } catch (_: JsonParseException) {
        null
    }

    fun encodeHistory(entries: List<QueryHistoryEntry>): String = gson.toJson(entries.map { it.toJson() })

    fun decodeHistory(json: String?): List<QueryHistoryEntry> {
        if (json.isNullOrBlank()) return emptyList()
        val raw: List<HistoryJson> = parseOrNull { gson.fromJson(json, historyType) } ?: emptyList()
        return raw.mapNotNull { it.toDomain() }
    }

    fun encodeFavorites(favorites: List<Favorite>): String = gson.toJson(favorites.map { it.toJson() })

    fun decodeFavorites(json: String?): List<Favorite> {
        if (json.isNullOrBlank()) return emptyList()
        val raw: List<FavoriteJson> = parseOrNull { gson.fromJson(json, favoritesType) } ?: emptyList()
        return raw.mapNotNull { it.toDomain() }
    }

    fun encodeSnapshot(snapshot: SyncSnapshot): String = gson.toJson(
        SnapshotJson(
            preferences = PreferencesJson(
                snapshot.preferences.language.code,
                snapshot.preferences.darkTheme,
                snapshot.preferences.speakResults,
            ),
            history = snapshot.history.map { it.toJson() },
            favorites = snapshot.favorites.map { it.toJson() },
            updatedAtMillis = snapshot.updatedAtMillis,
        ),
    )

    /** Devuelve null si el JSON no es válido o no trae la marca de tiempo. */
    fun decodeSnapshot(json: String): SyncSnapshot? {
        val raw = parseOrNull { gson.fromJson(json, SnapshotJson::class.java) } ?: return null
        val updatedAt = raw.updatedAtMillis ?: return null
        val defaults = UserPreferences()
        return SyncSnapshot(
            preferences = UserPreferences(
                language = AppLanguage.entries.firstOrNull { it.code == raw.preferences?.language } ?: defaults.language,
                darkTheme = raw.preferences?.darkTheme ?: defaults.darkTheme,
                speakResults = raw.preferences?.speakResults ?: defaults.speakResults,
            ),
            history = raw.history.orEmpty().mapNotNull { it.toDomain() },
            favorites = raw.favorites.orEmpty().mapNotNull { it.toDomain() },
            updatedAtMillis = updatedAt,
        )
    }
}
