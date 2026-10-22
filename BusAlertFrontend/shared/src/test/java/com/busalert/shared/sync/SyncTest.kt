package com.busalert.shared.sync

import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.QueryHistoryEntry
import com.busalert.shared.preferences.SyncSnapshot
import com.busalert.shared.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncTest {

    private val snapshot = SyncSnapshot(
        preferences = UserPreferences(AppLanguage.ENGLISH, darkTheme = false, speakResults = true),
        history = listOf(QueryHistoryEntry("Centro", "Terminal", listOf("FX1", "FX2"), 5L)),
        favorites = listOf(Favorite(FavoriteKind.STOP, "Parque Nariño", "Parque Nariño")),
        updatedAtMillis = 42L,
    )

    @Test
    fun `el payload ida y vuelta conserva todo`() {
        assertEquals(snapshot, SyncCodec.decodeSnapshot(SyncCodec.encodeSnapshot(snapshot)))
    }

    @Test
    fun `payloads inválidos o de otra versión se ignoran`() {
        assertNull(SyncCodec.decodeSnapshot("no es json"))
        assertNull(SyncCodec.decodeSnapshot("""{"preferences":{}}""")) // sin marca de tiempo
        assertNull(decodeSyncPayload(SyncContract.SCHEMA_VERSION + 1, SyncCodec.encodeSnapshot(snapshot)))
        assertNull(decodeSyncPayload(SyncContract.SCHEMA_VERSION, null))
        assertEquals(snapshot, decodeSyncPayload(SyncContract.SCHEMA_VERSION, SyncCodec.encodeSnapshot(snapshot)))
    }

    @Test
    fun `tolera campos desconocidos y entradas incompletas`() {
        val json = """
            {"preferences":{"language":"xx"},"history":[{"destination":null},{"destination":"UCC"}],
             "favorites":[{"kind":"OTRO","id":"x"},{"kind":"ROUTE","id":"E1"}],"updatedAtMillis":7,"extra":1}
        """.trimIndent()
        val decoded = SyncCodec.decodeSnapshot(json)!!
        assertEquals(AppLanguage.SPANISH, decoded.preferences.language)
        assertEquals(listOf("UCC"), decoded.history.map { it.destination })
        assertEquals(listOf(Favorite(FavoriteKind.ROUTE, "E1", "E1")), decoded.favorites)
    }

    /** Repositorio en memoria que registra si se aplicó un estado remoto. */
    private class MemoryRepo : PreferencesRepository {
        var state = snapshotOf(0L)
        override val preferences: Flow<UserPreferences> = MutableStateFlow(UserPreferences())
        override val history: Flow<List<QueryHistoryEntry>> = MutableStateFlow(emptyList())
        override val favorites: Flow<List<Favorite>> = MutableStateFlow(emptyList())
        override suspend fun setLanguage(language: AppLanguage) = touch { copy(preferences = preferences.copy(language = language)) }
        override suspend fun setDarkTheme(enabled: Boolean) = touch { copy(preferences = preferences.copy(darkTheme = enabled)) }
        override suspend fun setSpeakResults(enabled: Boolean) = touch { copy(preferences = preferences.copy(speakResults = enabled)) }
        override suspend fun addToHistory(entry: QueryHistoryEntry) = touch { copy(history = listOf(entry) + history) }
        override suspend fun clearHistory() = touch { copy(history = emptyList()) }
        override suspend fun toggleFavorite(favorite: Favorite) = touch { copy(favorites = favorites + favorite) }
        override suspend fun snapshot() = state
        override suspend fun applySnapshot(snapshot: SyncSnapshot): Boolean {
            if (snapshot.updatedAtMillis <= state.updatedAtMillis) return false
            state = snapshot
            return true
        }

        private fun touch(change: SyncSnapshot.() -> SyncSnapshot) {
            state = state.change().copy(updatedAtMillis = state.updatedAtMillis + 1)
        }

        companion object {
            fun snapshotOf(at: Long) = SyncSnapshot(UserPreferences(), emptyList(), emptyList(), at)
        }
    }

    @Test
    fun `cada cambio local se publica con el estado completo`() = runTest {
        val local = MemoryRepo()
        val published = mutableListOf<SyncSnapshot>()
        val repo = SyncingPreferencesRepository(local) { published += it }

        repo.setLanguage(AppLanguage.ENGLISH)
        repo.toggleFavorite(Favorite(FavoriteKind.ROUTE, "E1", "E1"))
        repo.addToHistory(QueryHistoryEntry(null, "Terminal", emptyList(), 1L))
        repo.setDarkTheme(false)
        repo.setSpeakResults(false)
        repo.clearHistory()

        assertEquals(6, published.size)
        assertEquals(local.state, published.last())
        assertEquals(AppLanguage.ENGLISH, published.last().preferences.language)
    }

    @Test
    fun `lo recibido del otro dispositivo no se vuelve a publicar`() = runTest {
        val published = mutableListOf<SyncSnapshot>()
        val repo = SyncingPreferencesRepository(MemoryRepo()) { published += it }
        assertTrue(repo.applySnapshot(snapshot))
        assertTrue(published.isEmpty())
    }
}
