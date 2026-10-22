package com.busalert.shared.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.busalert.shared.domain.AppLanguage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun TestScope.repository(file: File = tmp.newFile("prefs.preferences_pb").also { it.delete() }) =
        DataStorePreferencesRepository(
            PreferenceDataStoreFactory.create(
                scope = TestScope(UnconfinedTestDispatcher(testScheduler)).backgroundScope,
                produceFile = { file },
            ),
        )

    private fun entry(destination: String, origin: String? = null, at: Long = 0) =
        QueryHistoryEntry(origin, destination, listOf("FX1"), at)

    @Test
    fun `sin datos devuelve los valores por defecto`() = runTest {
        val repo = repository()
        assertEquals(UserPreferences(), repo.preferences.first())
        assertTrue(repo.history.first().isEmpty())
    }

    @Test
    fun `guarda idioma, tema y voz`() = runTest {
        val repo = repository()
        repo.setLanguage(AppLanguage.ENGLISH)
        repo.setDarkTheme(false)
        repo.setSpeakResults(false)
        assertEquals(UserPreferences(AppLanguage.ENGLISH, darkTheme = false, speakResults = false), repo.preferences.first())
    }

    @Test
    fun `el historial queda en orden inverso y recortado al máximo`() = runTest {
        val repo = repository()
        repeat(PreferenceDefaults.MAX_HISTORY_ENTRIES + 3) { i -> repo.addToHistory(entry("Destino $i", at = i.toLong())) }
        val history = repo.history.first()
        assertEquals(PreferenceDefaults.MAX_HISTORY_ENTRIES, history.size)
        assertEquals("Destino ${PreferenceDefaults.MAX_HISTORY_ENTRIES + 2}", history.first().destination)
    }

    @Test
    fun `replaceAll y clearHistory`() = runTest {
        val repo = repository()
        repo.replaceAll(UserPreferences(AppLanguage.ENGLISH), listOf(entry("Terminal")))
        assertEquals(AppLanguage.ENGLISH, repo.preferences.first().language)
        assertEquals(listOf("Terminal"), repo.history.first().map { it.destination })
        repo.clearHistory()
        assertTrue(repo.history.first().isEmpty())
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
}
