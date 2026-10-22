package com.busalert.mobile.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Paleta de marca. Es el ÚNICO lugar con colores literales en la app del teléfono. */
private object Palette {
    val Wine900 = Color(0xFF3B0013)
    val Wine700 = Color(0xFF7B0D2A)
    val Wine600 = Color(0xFF9C1C3C)
    val Wine300 = Color(0xFFFFB1C1)
    val Wine100 = Color(0xFFFFD9DF)
    val Wine50 = Color(0xFFFFF8F8)
    val Neutral10 = Color(0xFF201A1B)
    val Neutral20 = Color(0xFF362F30)
    val Neutral90 = Color(0xFFECE0E0)
    val Neutral95 = Color(0xFFFAEEEE)
    val NeutralVariant30 = Color(0xFF524345)
    val NeutralVariant50 = Color(0xFF857374)
    val NeutralVariant80 = Color(0xFFD7C1C3)
    val White = Color(0xFFFFFFFF)
    val Black = Color(0xFF000000)
    val ErrorLight = Color(0xFFBA1A1A)
    val ErrorDark = Color(0xFFFFB4AB)
    val DemoLight = Color(0xFF7A4F00)
    val DemoDark = Color(0xFFFFC94D)
}

private val LightScheme = lightColorScheme(
    primary = Palette.Wine700,
    onPrimary = Palette.White,
    primaryContainer = Palette.Wine100,
    onPrimaryContainer = Palette.Wine900,
    secondary = Palette.Wine600,
    onSecondary = Palette.White,
    background = Palette.Wine50,
    onBackground = Palette.Neutral10,
    surface = Palette.Wine50,
    onSurface = Palette.Neutral10,
    surfaceVariant = Palette.Neutral95,
    onSurfaceVariant = Palette.NeutralVariant30,
    outline = Palette.NeutralVariant50,
    error = Palette.ErrorLight,
    onError = Palette.White,
)

private val DarkScheme = darkColorScheme(
    primary = Palette.Wine300,
    onPrimary = Palette.Wine900,
    primaryContainer = Palette.Wine700,
    onPrimaryContainer = Palette.Wine100,
    secondary = Palette.Wine300,
    onSecondary = Palette.Wine900,
    background = Palette.Black,
    onBackground = Palette.Neutral90,
    surface = Palette.Neutral10,
    onSurface = Palette.Neutral90,
    surfaceVariant = Palette.Neutral20,
    onSurfaceVariant = Palette.NeutralVariant80,
    outline = Palette.NeutralVariant50,
    error = Palette.ErrorDark,
    onError = Palette.Wine900,
)

/** Colores que Material 3 no cubre. */
@Immutable
data class ExtraColors(val demo: Color)

/** Espaciado y tamaños de la app del teléfono. */
@Immutable
data class Dimens(
    val xs: Dp = 4.dp,
    val s: Dp = 8.dp,
    val m: Dp = 12.dp,
    val l: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val screenPadding: Dp = 16.dp,
    val mapHeight: Dp = 220.dp,
    val routeBadgeMinWidth: Dp = 48.dp,
    val stopOrderWidth: Dp = 32.dp,
)

private val LocalExtraColors = staticCompositionLocalOf { ExtraColors(demo = Palette.DemoLight) }
private val LocalDimens = staticCompositionLocalOf { Dimens() }

@Composable
fun BusAlertMobileTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    CompositionLocalProvider(
        LocalExtraColors provides ExtraColors(demo = if (darkTheme) Palette.DemoDark else Palette.DemoLight),
        LocalDimens provides Dimens(),
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

/** Acceso a los tokens: `AppTheme.dimens.l`, `AppTheme.extraColors.demo`. */
object AppTheme {
    val dimens: Dimens
        @Composable @ReadOnlyComposable get() = LocalDimens.current
    val extraColors: ExtraColors
        @Composable @ReadOnlyComposable get() = LocalExtraColors.current
}

private val HEX_COLOR = Regex("^#[0-9A-Fa-f]{6}$")
private const val HEX_RADIX = 16
private const val OPAQUE_ALPHA = 0xFF000000

/** Color de ruta definido en los datos (#RRGGBB), o null si no hay o no es válido. */
fun parseHexColor(hex: String?): Color? {
    if (hex == null || !HEX_COLOR.matches(hex)) return null
    return Color(OPAQUE_ALPHA or hex.substring(1).toLong(HEX_RADIX))
}
