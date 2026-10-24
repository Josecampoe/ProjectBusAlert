package com.busalert.wear.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Roles de color semánticos. Los composables nunca usan colores literales. */
@Immutable
data class BusAlertColors(
    val isDark: Boolean,
    val background: Color,
    val backgroundBrush: Brush,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val primary: Color,
    val onPrimary: Color,
    val accent: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onSurfaceMuted: Color,
    val error: Color,
    val demo: Color,
)

internal val DarkColors = BusAlertColors(
    isDark = true,
    background = Palette.Black,
    backgroundBrush = Brush.radialGradient(listOf(Palette.DarkGlow, Palette.DarkGlowEdge, Palette.Black)),
    surface = Palette.DarkSurface,
    surfaceVariant = Palette.DarkSurfaceVariant,
    outline = Palette.DarkOutline,
    primary = Palette.Wine500,
    onPrimary = Palette.White,
    accent = Palette.Wine400,
    onSurface = Palette.White,
    onSurfaceVariant = Palette.TextOnDarkSecondary,
    onSurfaceMuted = Palette.TextOnDarkMuted,
    error = Palette.ErrorOnDark,
    demo = Palette.DemoOnDark,
)

internal val LightColors = BusAlertColors(
    isDark = false,
    background = Palette.Wine50,
    backgroundBrush = Brush.radialGradient(listOf(Palette.White, Palette.LightGlowMid, Palette.LightGlowEdge)),
    surface = Palette.White,
    surfaceVariant = Palette.Wine100,
    outline = Palette.LightOutline,
    primary = Palette.Wine700,
    onPrimary = Palette.White,
    accent = Palette.Wine500,
    onSurface = Palette.TextOnLightPrimary,
    onSurfaceVariant = Palette.TextOnLightSecondary,
    onSurfaceMuted = Palette.TextOnLightMuted,
    error = Palette.ErrorOnLight,
    demo = Palette.DemoOnLight,
)

/** Escala de espaciado. */
@Immutable
data class BusAlertSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val s: Dp = 6.dp,
    val m: Dp = 8.dp,
    val l: Dp = 12.dp,
    val xl: Dp = 16.dp,
    val xxl: Dp = 24.dp,
    /** Margen lateral del contenido para no chocar con el borde redondo. */
    val screenHorizontal: Dp = 12.dp,
)

/** Tamaños de elementos (táctiles ≥ 48 dp según las guías de Wear OS). */
@Immutable
data class BusAlertDimens(
    val minTouchTarget: Dp = 48.dp,
    val micButton: Dp = 64.dp,
    val micHalo: Dp = 96.dp,
    val iconSmall: Dp = 16.dp,
    val iconMedium: Dp = 24.dp,
    val iconLarge: Dp = 32.dp,
    val progress: Dp = 44.dp,
    val progressStroke: Dp = 4.dp,
    val waveBarWidth: Dp = 4.dp,
    val waveBarMaxHeight: Dp = 32.dp,
    val divider: Dp = 1.dp,
    val outline: Dp = 1.dp,
    val routeBadgeMinWidth: Dp = 48.dp,
)

/** Duraciones de animación (ms). */
@Immutable
data class BusAlertMotion(
    val screenTransitionMs: Int = 220,
    val pulseMs: Int = 1_600,
    val pulseStaggerMs: Int = 500,
    val waveBaseMs: Int = 450,
    val waveStepMs: Int = 40,
    val waveDelaysMs: List<Int> = listOf(0, 120, 240, 60, 300, 180, 90),
)

/** Opacidades y escalas usadas en animaciones y estados. */
@Immutable
data class BusAlertEffects(
    val haloStartScale: Float = 0.7f,
    val haloEndScale: Float = 1f,
    val haloStartAlpha: Float = 0.35f,
    val haloEndAlpha: Float = 0f,
    val waveMinFraction: Float = 0.2f,
    val waveMaxFraction: Float = 1f,
)
