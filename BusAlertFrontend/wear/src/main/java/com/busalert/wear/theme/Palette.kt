package com.busalert.wear.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta de marca BusAlert (vinotinto). Es el ÚNICO archivo con valores de
 * color literales; los composables usan los roles semánticos de
 * [BusAlertColors] a través de `BusAlertTheme.colors`.
 */
internal object Palette {
    // Vinotinto
    val Wine900 = Color(0xFF22000B)
    val Wine800 = Color(0xFF4A0019)
    val Wine700 = Color(0xFF7B0D2A)
    val Wine500 = Color(0xFFC0264D)
    val Wine400 = Color(0xFFE83A65)
    val Wine100 = Color(0xFFF6E3E8)
    val Wine50 = Color(0xFFFAF2F4)

    // Superficies oscuras (OLED)
    val Black = Color(0xFF000000)
    val DarkSurface = Color(0xFF1D040C)
    val DarkSurfaceVariant = Color(0xFF2E0813)
    val DarkOutline = Color(0xFF5A1A2B)
    val DarkGlow = Color(0xFF24040E)
    val DarkGlowEdge = Color(0xFF0F0105)

    // Texto
    val White = Color(0xFFFFFFFF)
    val TextOnDarkSecondary = Color(0xFFE0C4CA)
    val TextOnDarkMuted = Color(0xFFC09AA4)
    val TextOnLightPrimary = Color(0xFF38020E)
    val TextOnLightSecondary = Color(0xFF6B2B3B)
    val TextOnLightMuted = Color(0xFF7D4552)

    // Superficies claras
    val LightOutline = Color(0xFFD9A3B0)
    val LightGlowEdge = Color(0xFFF2D0D8)
    val LightGlowMid = Color(0xFFFBECEF)

    // Estados
    val ErrorOnDark = Color(0xFFFF8A80)
    val ErrorOnLight = Color(0xFFB71C1C)
    val DemoOnDark = Color(0xFFFFC94D)
    val DemoOnLight = Color(0xFF7A4F00)
}
