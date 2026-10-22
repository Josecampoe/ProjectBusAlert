package com.busalert.shared.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.busalert.shared.domain.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private var now = START

    private fun TestScope.repository() = DataStorePreferencesRepository(
        PreferenceDataStoreFactory.create(
            scope = TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope,
            produceFile = { tmp.newFile("prefs.preferences_pb").also { it.delete() } },
        ),
        clock = { now },
    )

    private fun entry(destination: String, origin: String? = null, at: Long = 0) =
        QueryHistoryEntry(origin, destination, listOf("FX1"), at)

    @Test
    fun `sin datos devuelve los valores por defecto`() = runTest {
        val repo = repository()
        assertEquals(UserPreferences(), repo.preferences.first())
        assertTrue(repo.history.first().isEmpty())
        assertTrue(repo.favorites.first().isEmpty())
        assertEquals(0L, repo.snapshot().updatedAtMillis)
    }

    @Test
    fun `guarda idioma, tema y voz y marca la hora del cambio`() = runTest {
        val repo = repository()
        repo.setLanguage(AppLanguage.ENGLISH)
        repo.setDarkTheme(false)
        now = START + 1
        repo.setSpeakResults(false)
        assertEquals(UserPreferences(AppLanguage.ENGLISH, darkTheme = false, speakResults = false), repo.preferences.first())
        assertEquals(START + 1, repo.snapshot().updatedAtMillis)
    }

    @Test
    fun `el historial queda en orden inverso y recortado al máximo`() = runTest {
        val repo = repository()
        repeat(PreferenceDefaults.MAX_HISTORY_ENTRIES + 3) { i -> repo.addToHistory(entry("Destino $i", at = i.toLong())) }
        val history = repo.history.first()
        assertEquals(PreferenceDefaults.MAX_HISTORY_ENTRIES, history.size)
        assertEquals("Destino ${PreferenceDefaults.MAX_HISTORY_ENTRIES + 2}", history.first().destination)
        repo.clearHistory()
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun `los favoritos se agregan y se quitan con el mismo gesto`() = runTest {
        val repo = repository()
        val route = Favorite(FavoriteKind.ROUTE, "E1", "E1 — Dolores / Udenar")
        repo.toggleFavorite(route)
        repo.toggleFavorite(Favorite(FavoriteKind.STOP, "Parque Nariño", "Parque Nariño"))
        assertEquals(listOf("Parque Nariño", "E1"), repo.favorites.first().map { it.id })
        repo.toggleFavorite(route)
        assertEquals(listOf("Parque Nariño"), repo.favorites.first().map { it.id })
    }

    @Test
    fun `aplica un estado remoto solo si es más reciente`() = runTest {
        val repo = repository()
        repo.setLanguage(AppLanguage.SPANISH) // cambio local a la hora START
        val remote = SyncSnapshot(
            preferences = UserPreferences(AppLanguage.ENGLISH, darkTheme = false),
            history = listOf(entry("Terminal")),
            favorites = listOf(Favorite(FavoriteKind.ROUTE, "E1", "E1")),
            updatedAtMillis = START + 10,
        )
        assertTrue(repo.applySnapshot(remote))
        assertEquals(remote, repo.snapshot())

        // Un estado más viejo (o el mismo, que vuelve como eco) no se aplica.
        assertFalse(repo.applySnapshot(remote.copy(preferences = UserPreferences(), updatedAtMillis = START + 5)))
        assertFalse(repo.applySnapshot(remote))
        assertEquals(AppLanguage.ENGLISH, repo.preferences.first().language)
    }

    @Test
    fun `lugares recientes sin repetir y con límite`() {
        val history = listOf(
            entry("Terminal", origin = "Centro"),
            entry("terminal", origin = "Udenar"),
            entry("UCC"),
        )
        assertEquals(listOf("Terminal", "Centro", "Udenar", "UCC"), history.recentPlaces())
        assertEquals(listOf("Terminal", "Centro"), history.recentPlaces(limit = 2))
    }

    private companion object {
        const val START = 1_000L
    }
}
