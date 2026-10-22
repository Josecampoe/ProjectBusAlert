package com.busalert.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

/**
 * Tokens de color para la interfaz de BusAlert Wear OS.
 * Soporta Modo Oscuro OLED premium y Modo Claro refinado.
 */
data class BusAlertThemeTokens(
    val isDark: Boolean,
    val bgBrush: Brush,
    val background: Color,
    val surface: Color,
    val cardBorder: Color,
    val primary: Color,
    val primaryAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val chipBackground: Color,
    val chipBorder: Color,
    val micRings: Color
)

val DarkTokens = BusAlertThemeTokens(
    isDark          = true,
    bgBrush         = BgRadialDark,
    background      = OledBlack,
    surface         = DarkCardSurface,
    cardBorder      = DarkCardBorder,
    primary         = VinotintoLight,
    primaryAccent   = VinotintoGlow,
    textPrimary     = DarkTextPrimary,
    textSecondary   = DarkTextSecondary,
    textMuted       = DarkTextMuted,
    chipBackground  = Color(0x333A0B17),
    chipBorder      = Color(0x66C0264D),
    micRings        = VinotintoGlow
)

val LightTokens = BusAlertThemeTokens(
    isDark          = false,
    bgBrush         = BgRadialLight,
    background      = LightBackground,
    surface         = LightSurface,
    cardBorder      = LightCardBorder,
    primary         = Vinotinto,
    primaryAccent   = VinotintoLight,
    textPrimary     = LightTextPrimary,
    textSecondary   = LightTextSecondary,
    textMuted       = LightTextMuted,
    chipBackground  = Color(0x1F7B0D2A),
    chipBorder      = Color(0x407B0D2A),
    micRings        = Vinotinto
)

val LocalBusAlertTheme = staticCompositionLocalOf { DarkTokens }

@Composable
fun BusAlertTheme(
    isDark: Boolean = true,
    content: @Composable () -> Unit
) {
    val tokens = if (isDark) DarkTokens else LightTokens

    val wearColors = if (isDark) {
        Colors(
            primary          = tokens.primary,
            primaryVariant   = VinotintoDark,
            secondary        = tokens.primaryAccent,
            secondaryVariant = VinotintoLight,
            background       = tokens.background,
            surface          = tokens.surface,
            onPrimary        = PureWhite,
            onSecondary      = PureWhite,
            onBackground     = tokens.textPrimary,
            onSurface        = tokens.textPrimary,
            error            = Color(0xFFFF5252),
            onError          = PureWhite
        )
    } else {
        Colors(
            primary          = tokens.primary,
            primaryVariant   = VinotintoDark,
            secondary        = tokens.primaryAccent,
            secondaryVariant = VinotintoDark,
            background       = tokens.background,
            surface          = tokens.surface,
            onPrimary        = PureWhite,
            onSecondary      = PureWhite,
            onBackground     = tokens.textPrimary,
            onSurface        = tokens.textPrimary,
            error            = Color(0xFFB71C1C),
            onError          = PureWhite
        )
    }

    CompositionLocalProvider(LocalBusAlertTheme provides tokens) {
        MaterialTheme(
            colors  = wearColors,
            content = content
        )
    }
}
