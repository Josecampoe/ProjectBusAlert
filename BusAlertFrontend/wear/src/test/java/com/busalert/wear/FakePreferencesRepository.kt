package com.busalert.wear

import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.PreferenceDefaults
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.UserPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias en memoria para pruebas. */
class FakePreferencesRepository(
    initial: UserPreferences = UserPreferences(),
) : PreferencesRepository {
    override val preferences: StateFlow<UserPreferences> = MutableStateFlow(initial)
    override val history: StateFlow<List<QueryHistoryEntry>> = MutableStateFlow(emptyList())

    private val prefs get() = preferences as MutableStateFlow
    private val hist get() = history as MutableStateFlow

    override suspend fun setLanguage(language: AppLanguage) {
        prefs.value = prefs.value.copy(language = language)
    }

    override suspend fun setDarkTheme(enabled: Boolean) {
        prefs.value = prefs.value.copy(darkTheme = enabled)
    }

    override suspend fun setSpeakResults(enabled: Boolean) {
        prefs.value = prefs.value.copy(speakResults = enabled)
    }

    override suspend fun addToHistory(entry: QueryHistoryEntry) {
        hist.value = (listOf(entry) + hist.value).take(PreferenceDefaults.MAX_HISTORY_ENTRIES)
    }

    override suspend fun clearHistory() {
        hist.value = emptyList()
    }

    override suspend fun replaceAll(preferences: UserPreferences, history: List<QueryHistoryEntry>) {
        prefs.value = preferences
        hist.value = history
    }
}
