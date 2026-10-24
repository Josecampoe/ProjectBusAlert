package com.busalert.mobile

import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.PreferenceDefaults
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.SyncSnapshot
import com.busalert.shared.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow

/** Preferencias en memoria para pruebas. */
class FakePreferencesRepository(initial: UserPreferences = UserPreferences()) : PreferencesRepository {
    override val preferences = MutableStateFlow(initial)
    override val history = MutableStateFlow<List<QueryHistoryEntry>>(emptyList())
    override val favorites = MutableStateFlow<List<Favorite>>(emptyList())
    private var updatedAt = 0L

    override suspend fun setLanguage(language: AppLanguage) {
        preferences.value = preferences.value.copy(language = language)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        preferences.value = preferences.value.copy(darkTheme = enabled)
    }

    override suspend fun setSpeakResults(enabled: Boolean) {
        preferences.value = preferences.value.copy(speakResults = enabled)
    }

    override suspend fun addToHistory(entry: QueryHistoryEntry) {
        history.value = (listOf(entry) + history.value).take(PreferenceDefaults.MAX_HISTORY_ENTRIES)
    }

    override suspend fun clearHistory() {
        history.value = emptyList()
    }

    override suspend fun toggleFavorite(favorite: Favorite) {
        favorites.value = if (favorite in favorites.value) favorites.value - favorite else listOf(favorite) + favorites.value
    }

    override suspend fun snapshot() = SyncSnapshot(preferences.value, history.value, favorites.value, updatedAt)

    override suspend fun applySnapshot(snapshot: SyncSnapshot): Boolean {
        if (snapshot.updatedAtMillis <= updatedAt) return false
        preferences.value = snapshot.preferences
        history.value = snapshot.history
        favorites.value = snapshot.favorites
        updatedAt = snapshot.updatedAtMillis
        return true
    }
}
