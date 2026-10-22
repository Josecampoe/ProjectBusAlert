package com.busalert.wear.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verificaciones de accesibilidad del sistema de diseño. */
class ThemeTest {

    /** Contraste WCAG entre dos colores. */
    private fun contrast(a: Color, b: Color): Double {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }

    private fun assertReadable(name: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue("$name: contraste $ratio < $MIN_TEXT_CONTRAST", ratio >= MIN_TEXT_CONTRAST)
    }

    @Test
    fun `el texto cumple contraste AA en ambos temas`() {
        for (c in listOf(DarkColors, LightColors)) {
            val theme = if (c.isDark) "oscuro" else "claro"
            for ((name, fg) in listOf(
                "onSurface" to c.onSurface,
                "onSurfaceVariant" to c.onSurfaceVariant,
                "onSurfaceMuted" to c.onSurfaceMuted,
                "accent" to c.accent,
                "error" to c.error,
                "demo" to c.demo,
            )) {
                assertReadable("$theme/$name sobre fondo", fg, c.background)
                assertReadable("$theme/$name sobre superficie", fg, c.surface)
            }
            assertReadable("$theme/onPrimary sobre primary", c.onPrimary, c.primary)
        }
    }

    @Test
    fun `el color de ruta solo acepta #RRGGBB`() {
        assertEquals(Color(0xFF1565C0), parseHexColor("#1565C0"))
        assertNull(parseHexColor("1565C0"))
        assertNull(parseHexColor("#FFF"))
        assertNull(parseHexColor(null))
    }

    private companion object {
        /** WCAG 2.1 AA para texto normal. */
        const val MIN_TEXT_CONTRAST = 4.5
    }
}
