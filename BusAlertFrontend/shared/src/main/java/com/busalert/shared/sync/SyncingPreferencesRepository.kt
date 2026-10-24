package com.busalert.shared.sync

import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.SyncSnapshot

/** Envía el estado del usuario al otro dispositivo. */
fun interface SyncPublisher {
    suspend fun publish(snapshot: SyncSnapshot)
}

/**
 * Decorador: después de cada cambio local publica el estado completo.
 * Lo que llega del otro dispositivo ([applySnapshot]) no se vuelve a publicar,
 * así se evitan bucles entre reloj y teléfono.
 */
class SyncingPreferencesRepository(
    private val local: PreferencesRepository,
    private val publisher: SyncPublisher,
) : PreferencesRepository by local {

    private suspend fun publishAfter(change: suspend () -> Unit) {
        change()
        publisher.publish(local.snapshot())
    }

    override suspend fun setLanguage(language: AppLanguage) = publishAfter { local.setLanguage(language) }
    override suspend fun setDarkTheme(enabled: Boolean) = publishAfter { local.setDarkTheme(enabled) }
    override suspend fun setSpeakResults(enabled: Boolean) = publishAfter { local.setSpeakResults(enabled) }
    override suspend fun addToHistory(entry: QueryHistoryEntry) = publishAfter { local.addToHistory(entry) }
    override suspend fun clearHistory() = publishAfter { local.clearHistory() }
    override suspend fun toggleFavorite(favorite: Favorite) = publishAfter { local.toggleFavorite(favorite) }
}
