package com.busalert.mobile.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.UserPreferences
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Historial, favoritos y ajustes del usuario (sincronizados con el reloj). */
class UserDataViewModel(private val repository: PreferencesRepository) : ViewModel() {

    val preferences: StateFlow<UserPreferences> =
        repository.preferences.stateIn(viewModelScope, SharingStarted.Eagerly, UserPreferences())
    val history: StateFlow<List<QueryHistoryEntry>> =
        repository.history.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val favorites: StateFlow<List<Favorite>> =
        repository.favorites.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch { repository.setLanguage(language) }
    }

    fun setDarkTheme(enabled: Boolean) {
        viewModelScope.launch { repository.setDarkTheme(enabled) }
    }

    fun setSpeakResults(enabled: Boolean) {
        viewModelScope.launch { repository.setSpeakResults(enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun removeFavorite(favorite: Favorite) {
        viewModelScope.launch { repository.toggleFavorite(favorite) }
    }

    class Factory(private val repository: PreferencesRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserDataViewModel(repository) as T
    }
}
