package com.busalert.wear.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Shapes
import androidx.wear.compose.material.Typography

private val LocalColors = staticCompositionLocalOf { DarkColors }
private val LocalSpacing = staticCompositionLocalOf { BusAlertSpacing() }
private val LocalDimens = staticCompositionLocalOf { BusAlertDimens() }
private val LocalMotion = staticCompositionLocalOf { BusAlertMotion() }
private val LocalEffects = staticCompositionLocalOf { BusAlertEffects() }

/**
 * Tema de BusAlert para Wear OS: colores semánticos (claro/oscuro), tipografía
 * escalable (sp de Wear Material), formas, espaciado, dimensiones y movimiento.
 */
@Composable
fun BusAlertTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalSpacing provides BusAlertSpacing(),
        LocalDimens provides BusAlertDimens(),
        LocalMotion provides BusAlertMotion(),
        LocalEffects provides BusAlertEffects(),
    ) {
        MaterialTheme(
            colors = colors.toWearColors(),
            typography = Typography(),
            shapes = Shapes(),
            content = content,
        )
    }
}

/** Acceso a los tokens: `BusAlertTheme.colors.primary`, `BusAlertTheme.spacing.m`... */
object BusAlertTheme {
    val colors: BusAlertColors
        @Composable @ReadOnlyComposable get() = LocalColors.current
    val spacing: BusAlertSpacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val dimens: BusAlertDimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
    val motion: BusAlertMotion
        @Composable @ReadOnlyComposable get() = LocalMotion.current
    val effects: BusAlertEffects
        @Composable @ReadOnlyComposable get() = LocalEffects.current
    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography
    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
}

private fun BusAlertColors.toWearColors() = Colors(
    primary = primary,
    primaryVariant = primary,
    secondary = accent,
    secondaryVariant = accent,
    background = background,
    surface = surface,
    error = error,
    onPrimary = onPrimary,
    onSecondary = onPrimary,
    onBackground = onSurface,
    onSurface = onSurface,
    onSurfaceVariant = onSurfaceVariant,
    onError = background,
)

/**
 * Color de una ruta: el definido en los datos del backend (#RRGGBB) o, si no
 * hay o no es válido, el color primario del tema.
 */
@Composable
@ReadOnlyComposable
fun routeColor(hex: String?): Color = parseHexColor(hex) ?: BusAlertTheme.colors.primary

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000

fun parseHexColor(hex: String?): Color? {
    if (hex == null || !HEX_COLOR.matches(hex)) return null
    return Color(OPAQUE_ALPHA or hex.substring(1).toLong(HEX_RADIX))
}
