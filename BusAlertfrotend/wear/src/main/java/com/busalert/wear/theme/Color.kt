package com.busalert.wear.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Vino tinto base ───────────────────────────────────────────
val Vinotinto          = Color(0xFF7B0D2A)   // Vino tinto insignia
val VinotintoDark      = Color(0xFF4A0019)   // Vino tinto muy oscuro
val VinotintoDeep      = Color(0xFF22000B)   // Ultra oscuro fondo vino
val VinotintoLight     = Color(0xFFC0264D)   // Acentos luminosos y activos
val VinotintoSoft      = Color(0xFFD4607A)   // Detalles secundarios
val VinotintoGlow      = Color(0xFFE83A65)   // Brillo neón para modo oscuro

// ── Modo Oscuro OLED (Máxima elegancia y ahorro de batería) ───
val OledBlack          = Color(0xFF000000)   // Negro puro OLED
val DarkSurfaceElevated= Color(0xFF140207)   // Superficie elevada
val DarkCardSurface    = Color(0xFF1D040C)   // Tarjeta vino ultra oscura
val DarkCardBorder     = Color(0xFF4A0E1D)   // Borde sutil para tarjetas oscuras
val DarkTextPrimary    = Color(0xFFFFFFFF)   // Texto blanco puro
val DarkTextSecondary  = Color(0xFFE0C4CA)   // Texto secundario contrastado
val DarkTextMuted      = Color(0xFF9E707C)   // Texto sutil atenuado

// ── Modo Claro Refinado (Limpio y suave) ───────────────────────
val LightBackground    = Color(0xFFFAF2F4)   // Crema suave con toque rosáceo
val LightSurface       = Color(0xFFFFFFFF)   // Superficie blanca pura
val LightCardBorder    = Color(0xFFE8B8C2)   // Borde vino suave
val LightTextPrimary   = Color(0xFF38020E)   // Texto vino profundo casi negro
val LightTextSecondary = Color(0xFF783344)   // Texto vino medio
val LightTextMuted     = Color(0xFFA06473)   // Texto sutil claro

// ── Blancos y Alphas ──────────────────────────────────────────
val PureWhite          = Color(0xFFFFFFFF)
val SoftWhite          = Color(0xFFF8F0F2)
val CreamWhite         = Color(0xFFFCEEF1)
val WhiteAlpha80       = Color(0xCCFFFFFF)
val WhiteAlpha50       = Color(0x80FFFFFF)
val WhiteAlpha20       = Color(0x33FFFFFF)
val WhiteAlpha10       = Color(0x1AFFFFFF)

// ── Fondos Radiales Dinámicos ─────────────────────────────────
val BgRadialDark = Brush.radialGradient(
    colors = listOf(Color(0xFF24040E), Color(0xFF0F0105), Color(0xFF000000))
)

val BgRadialLight = Brush.radialGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFFBECEF), Color(0xFFF2D0D8))
)

// Style: Contrastes estandarizados segun lineamientos Wear OS Material Design
