package com.busalert.wear.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.*
import com.busalert.shared.domain.NavigationInstruction
import com.busalert.wear.theme.*

// ─────────────────────────────────────────────────────────────
// HOME SCREEN
// Pantalla principal con selector de Modo Oscuro/Claro e Idioma (ES/EN)
// y el botón central de micrófono con pulsos dinámicos.
// ─────────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    isDark: Boolean,
    isEnglish: Boolean,
    onToggleDark: () -> Unit,
    onToggleLanguage: () -> Unit,
    onMicClick: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current
    val strings = remember(isEnglish) { AppStrings(isEnglish) }

    val inf = rememberInfiniteTransition(label = "home")

    val ring1 by inf.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, 0, EaseOut), RepeatMode.Restart),
        label = "r1"
    )
    val ring1Alpha by inf.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, 0, EaseOut), RepeatMode.Restart),
        label = "a1"
    )
    val ring2 by inf.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, 500, EaseOut), RepeatMode.Restart),
        label = "r2"
    )
    val ring2Alpha by inf.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, 500, EaseOut), RepeatMode.Restart),
        label = "a2"
    )
    val ring3 by inf.animateFloat(
        initialValue = 0.75f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, 1000, EaseOut), RepeatMode.Restart),
        label = "r3"
    )
    val ring3Alpha by inf.animateFloat(
        initialValue = 0.5f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1600, 1000, EaseOut), RepeatMode.Restart),
        label = "a3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            // Título y Ciudad
            Text(
                text = strings.appName,
                color = tokens.primaryAccent,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = strings.cityName,
                color = tokens.textMuted,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // ── Barra de controles rápidos: Modo Oscuro / Idioma ──
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ThemeToggleChip(
                    isDark = isDark,
                    onClick = onToggleDark
                )
                LanguageToggleChip(
                    isEnglish = isEnglish,
                    onClick = onToggleLanguage
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ── Botón central de Micrófono con ondas ──────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(86.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(82.dp)
                        .scale(ring3)
                        .clip(CircleShape)
                        .background(tokens.micRings.copy(alpha = ring3Alpha * 0.25f))
                )
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .scale(ring2)
                        .clip(CircleShape)
                        .background(tokens.micRings.copy(alpha = ring2Alpha * 0.35f))
                )
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .scale(ring1)
                        .clip(CircleShape)
                        .background(tokens.micRings.copy(alpha = ring1Alpha * 0.45f))
                )

                Button(
                    onClick = onMicClick,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.primaryButtonColors(
                        backgroundColor = tokens.primary
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            WaveLine(width = 18.dp, alpha = 1f)
                            WaveLine(width = 12.dp, alpha = 0.75f)
                            WaveLine(width = 8.dp, alpha = 0.5f)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = strings.homeTapConsult,
                color = tokens.textMuted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// COMPONENTES DE CONTROL (Modo Oscuro & Idioma)
// ─────────────────────────────────────────────────────────────

@Composable
fun ThemeToggleChip(
    isDark: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tokens.chipBackground)
            .border(1.dp, tokens.chipBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = if (isDark) "🌙" else "☀️",
                fontSize = 9.sp
            )
            Text(
                text = if (isDark) "DARK" else "LIGHT",
                color = tokens.textSecondary,
                fontSize = 7.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
fun LanguageToggleChip(
    isEnglish: Boolean,
    onClick: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current

    Box(
        modifier = Modifier
            .height(22.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(tokens.chipBackground)
            .border(1.dp, tokens.chipBorder, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Segmento ES
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (!isEnglish) tokens.primary else Color.Transparent)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ES",
                    color = if (!isEnglish) PureWhite else tokens.textMuted,
                    fontSize = 8.sp,
                    fontWeight = if (!isEnglish) FontWeight.Bold else FontWeight.Normal
                )
            }

            // Segmento EN
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isEnglish) tokens.primary else Color.Transparent)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "EN",
                    color = if (isEnglish) PureWhite else tokens.textMuted,
                    fontSize = 8.sp,
                    fontWeight = if (isEnglish) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun WaveLine(width: Dp, alpha: Float) {
    Box(
        modifier = Modifier
            .width(width)
            .height(2.dp)
            .clip(RoundedCornerShape(50))
            .background(PureWhite.copy(alpha = alpha))
    )
}

// ─────────────────────────────────────────────────────────────
// LISTENING SCREEN (Escucha de Voz 100% In-App)
// ─────────────────────────────────────────────────────────────
@Composable
fun ListeningScreen(
    prompt: String = "Escuchando...",
    partialText: String = "",
    statusMessage: String? = null,
    isEnglish: Boolean = false,
    onRetryVoice: (() -> Unit)? = null,
    onUseKeyboard: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    val tokens = LocalBusAlertTheme.current
    val strings = remember(isEnglish) { AppStrings(isEnglish) }
    val inf = rememberInfiniteTransition(label = "audio")

    val delays = listOf(0, 120, 240, 60, 300, 180, 90)
    val heights = delays.mapIndexed { i, delay ->
        inf.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(450 + i * 40, delay, EaseInOutQuad),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$i"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Título de la consulta con botón de cancelar (✕)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (onCancel != null) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(tokens.chipBackground)
                            .border(1.dp, tokens.chipBorder, CircleShape)
                            .clickable { onCancel() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "✕",
                            color = tokens.textPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = prompt,
                    color = tokens.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }

            // Barras animadas de onda de sonido (Color Vinotinto)
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(30.dp)
            ) {
                heights.forEachIndexed { index, heightState ->
                    val barHeight = (30 * heightState.value).dp
                    val isCenter = index == 3
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(50))
                            .background(if (isCenter) tokens.primaryAccent else tokens.primary)
                    )
                }
            }

            // Texto escuchado en tiempo real o mensaje de estado
            if (partialText.isNotBlank()) {
                Text(
                    text = "\"$partialText...\"",
                    color = tokens.primaryAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            } else if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    color = tokens.textMuted,
                    fontSize = 8.5.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            } else {
                Text(
                    text = strings.listeningSpeakNow,
                    color = tokens.textMuted,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                )
            }

            // Botones de acción inferior: Reintentar Voz y/o Usar Teclado
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onRetryVoice != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(tokens.chipBackground)
                            .border(1.dp, tokens.chipBorder, RoundedCornerShape(11.dp))
                            .clickable { onRetryVoice() }
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🔄 Voz",
                            color = tokens.primaryAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onUseKeyboard != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(11.dp))
                            .background(tokens.primary)
                            .clickable { onUseKeyboard() }
                            .padding(horizontal = 8.dp, vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(text = "⌨️", fontSize = 8.5.sp)
                            Text(
                                text = if (isEnglish) "Keyboard" else "Teclado",
                                color = PureWhite,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// PROCESSING SCREEN
// ─────────────────────────────────────────────────────────────
@Composable
fun ProcessingScreen(isEnglish: Boolean = false) {
    val tokens = LocalBusAlertTheme.current
    val strings = remember(isEnglish) { AppStrings(isEnglish) }
    val inf = rememberInfiniteTransition(label = "proc")

    val pulse by inf.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(800, easing = EaseInOutQuad), RepeatMode.Reverse),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier.size(54.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.fillMaxSize(),
                    indicatorColor = tokens.primaryAccent,
                    trackColor = tokens.cardBorder,
                    strokeWidth = 3.dp
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(pulse)
                        .clip(CircleShape)
                        .background(tokens.primaryAccent)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = strings.processingTitle,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = strings.processingSubtitle,
                color = tokens.textMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// RESULT SCREEN
// ─────────────────────────────────────────────────────────────
@Composable
fun ResultScreen(
    result: NavigationInstruction,
    isEnglish: Boolean = false,
    onDismiss: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current
    val strings = remember(isEnglish) { AppStrings(isEnglish) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(14.dp))
                .background(tokens.surface)
                .border(1.dp, tokens.cardBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            // Chip de ruta
            result.recommendedRouteId?.let { id ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(tokens.primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${strings.routePrefix} $id",
                        color = PureWhite,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Título
            Text(
                text = result.displayTitle,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = 15.sp
            )

            // Línea divisora
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(1.dp)
                    .background(tokens.cardBorder)
            )

            // Subtítulo
            Text(
                text = result.displaySubtitle,
                color = tokens.textSecondary,
                fontSize = 9.5.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Botón cerrar
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(26.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = tokens.primary
                )
            ) {
                Text(
                    text = strings.resultClose,
                    color = PureWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ERROR SCREEN
// ─────────────────────────────────────────────────────────────
@Composable
fun ErrorScreen(
    message: String?,
    isEnglish: Boolean = false,
    onRetry: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current
    val strings = remember(isEnglish) { AppStrings(isEnglish) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tokens.chipBackground)
                    .border(1.5.dp, tokens.primaryAccent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.5.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(50))
                            .background(tokens.primaryAccent)
                    )
                    Box(
                        modifier = Modifier
                            .size(2.5.dp)
                            .clip(CircleShape)
                            .background(tokens.primaryAccent)
                    )
                }
            }

            Text(
                text = strings.errorTitle,
                color = tokens.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = message ?: strings.errorTitle,
                color = tokens.textMuted,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                maxLines = 2
            )

            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .height(26.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = tokens.primary
                )
            ) {
                Text(
                    text = strings.errorRetry,
                    color = PureWhite,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// ─────────────────────────────────────────────────────────────
// TYPING SCREEN (Teclado Wear OS 100% Nativo con Paleta Vinotinto)
// ─────────────────────────────────────────────────────────────
@Composable
fun TypingScreen(
    prompt: String,
    placeholder: String,
    inputText: String,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onUseVoice: (() -> Unit)? = null
) {
    val tokens = LocalBusAlertTheme.current
    var isSymbolsMode by remember { mutableStateOf(false) }
    var isUppercase by remember { mutableStateOf(true) }

    // Cursor parpadeante
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorBlink"
    )

    // Lugares frecuentes en Pasto para autocompletado rápido
    val quickLocations = remember {
        listOf(
            "UCC",
            "Udenar",
            "Centro",
            "Terminal",
            "Fátima",
            "Parque Nariño",
            "Hospital",
            "Lorenzo",
            "Catambuco",
            "Chambú"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(tokens.bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
        ) {
            // ── Título breve del paso (ej: ¿Dónde estás? o ¿A dónde vas?) ──
            Text(
                text = prompt,
                color = tokens.primaryAccent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // ── Barra Superior: Cancelar (✕), Entrada de texto, Micrófono (🎙️) y Enviar (✓) ──
            // Ancho limitado al 86% para que nunca colisione con el arco circular superior
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // Botón Cancelar (✕)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens.chipBackground)
                        .border(1.dp, tokens.chipBorder, CircleShape)
                        .clickable { onCancel() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        color = tokens.textPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Cuadro visor del texto ingresado con scroll horizontal
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(7.dp))
                        .background(tokens.surface)
                        .border(1.dp, tokens.cardBorder, RoundedCornerShape(7.dp))
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (inputText.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = tokens.textMuted.copy(alpha = 0.7f),
                            fontSize = 8.sp,
                            maxLines = 1
                        )
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = inputText,
                                color = tokens.textPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.5.dp)
                                    .height(10.dp)
                                    .background(tokens.primaryAccent.copy(alpha = cursorAlpha))
                            )
                        }
                    }
                }

                // Botón Cambiar a Voz (🎙️) si está disponible
                if (onUseVoice != null) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(tokens.chipBackground)
                            .border(1.dp, tokens.chipBorder, CircleShape)
                            .clickable { onUseVoice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎙️",
                            fontSize = 9.sp
                        )
                    }
                }

                // Botón Enviar (✓)
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens.primary)
                        .clickable {
                            if (inputText.isNotBlank()) onSubmit()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✓",
                        color = PureWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // ── Fila de Sugerencias Rápidas de Pasto (Centradas y con Scroll) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickLocations.forEach { loc ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(tokens.chipBackground)
                            .border(0.8.dp, tokens.chipBorder, RoundedCornerShape(5.dp))
                            .clickable {
                                if (inputText.isEmpty()) onTextChange(loc)
                                else onTextChange("$inputText $loc")
                            }
                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = loc,
                            color = tokens.textPrimary,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ── Teclado Virtual en Pantalla (Ajustado exactamente al arco circular) ──
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                if (!isSymbolsMode) {
                    // Modo Letras (QWERTY)
                    val row1 = listOf("Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P")
                    val row2 = listOf("A", "S", "D", "F", "G", "H", "J", "K", "L")
                    val row3 = listOf("Z", "X", "C", "V", "B", "N", "M")

                    // Fila 1 (10 teclas: ~163dp ancho total)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row1.forEach { char ->
                            val text = if (isUppercase) char else char.lowercase()
                            WearKey(text = text, width = 15.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + text)
                            }
                        }
                    }

                    // Fila 2 (9 teclas: ~156dp ancho total)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row2.forEach { char ->
                            val text = if (isUppercase) char else char.lowercase()
                            WearKey(text = text, width = 16.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + text)
                            }
                        }
                    }

                    // Fila 3: Shift + Letras + Borrar (~155dp ancho total)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shift
                        WearKey(
                            text = if (isUppercase) "⬆" else "⇧",
                            width = 19.dp,
                            height = 20.dp,
                            fontSize = 9.sp,
                            isSpecial = true,
                            accentColor = if (isUppercase) tokens.primary else null
                        ) {
                            isUppercase = !isUppercase
                        }

                        row3.forEach { char ->
                            val text = if (isUppercase) char else char.lowercase()
                            WearKey(text = text, width = 15.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + text)
                            }
                        }

                        // Backspace
                        WearKey(text = "⌫", width = 19.dp, height = 20.dp, fontSize = 9.sp, isSpecial = true) {
                            if (inputText.isNotEmpty()) {
                                onTextChange(inputText.dropLast(1))
                            }
                        }
                    }

                    // Fila 4: ?123, Espacio, Limpiar (~120dp ancho total: cabe perfecto en la base curva del reloj)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WearKey(text = "?123", width = 26.dp, height = 18.dp, fontSize = 7.5.sp, isSpecial = true) {
                            isSymbolsMode = true
                        }
                        WearKey(text = "Espacio", width = 64.dp, height = 18.dp, fontSize = 7.5.sp, isSpecial = true) {
                            onTextChange("$inputText ")
                        }
                        WearKey(text = "Limpiar", width = 26.dp, height = 18.dp, fontSize = 7.sp, isSpecial = true) {
                            onTextChange("")
                        }
                    }
                } else {
                    // Modo Números y Símbolos
                    val numRow = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
                    val symRow1 = listOf("-", "/", ":", ";", "(", ")", "$", "&", "@")
                    val symRow2 = listOf(".", ",", "?", "!", "#", "\"", "%")

                    // Fila Números
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        numRow.forEach { char ->
                            WearKey(text = char, width = 15.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + char)
                            }
                        }
                    }

                    // Fila Símbolos 1
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        symRow1.forEach { char ->
                            WearKey(text = char, width = 16.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + char)
                            }
                        }
                    }

                    // Fila Símbolos 2 + Borrar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        symRow2.forEach { char ->
                            WearKey(text = char, width = 16.dp, height = 20.dp, fontSize = 9.sp) {
                                onTextChange(inputText + char)
                            }
                        }
                        WearKey(text = "⌫", width = 19.dp, height = 20.dp, fontSize = 9.sp, isSpecial = true) {
                            if (inputText.isNotEmpty()) {
                                onTextChange(inputText.dropLast(1))
                            }
                        }
                    }

                    // Fila 4: ABC, Espacio, Limpiar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WearKey(text = "ABC", width = 26.dp, height = 18.dp, fontSize = 7.5.sp, isSpecial = true) {
                            isSymbolsMode = false
                        }
                        WearKey(text = "Espacio", width = 64.dp, height = 18.dp, fontSize = 7.5.sp, isSpecial = true) {
                            onTextChange("$inputText ")
                        }
                        WearKey(text = "Limpiar", width = 26.dp, height = 18.dp, fontSize = 7.sp, isSpecial = true) {
                            onTextChange("")
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tecla personalizada adaptable a Modo Claro y Modo Oscuro
 */
@Composable
private fun WearKey(
    text: String,
    width: Dp,
    height: Dp = 20.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 9.sp,
    isSpecial: Boolean = false,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    val tokens = LocalBusAlertTheme.current

    val bg = when {
        accentColor != null -> accentColor
        isSpecial -> tokens.chipBackground
        else -> tokens.surface
    }

    val border = when {
        accentColor != null -> accentColor
        isSpecial -> tokens.chipBorder
        else -> tokens.cardBorder
    }

    val textColor = when {
        accentColor != null -> PureWhite
        isSpecial -> tokens.primaryAccent
        else -> tokens.textPrimary
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(4.5.dp))
            .background(bg)
            .border(0.8.dp, border, RoundedCornerShape(4.5.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (isSpecial) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────
// PREVIEWS EN MODO OSCURO Y CLARO (Android Studio)
// ─────────────────────────────────────────────────────────────

@Preview(name = "1. Inicio - Modo Oscuro (ES)", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun HomeScreenDarkPreview() {
    BusAlertTheme(isDark = true) {
        HomeScreen(
            isDark = true,
            isEnglish = false,
            onToggleDark = {},
            onToggleLanguage = {},
            onMicClick = {}
        )
    }
}

@Preview(name = "1b. Inicio - Modo Claro (EN)", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun HomeScreenLightPreview() {
    BusAlertTheme(isDark = false) {
        HomeScreen(
            isDark = false,
            isEnglish = true,
            onToggleDark = {},
            onToggleLanguage = {},
            onMicClick = {}
        )
    }
}

@Preview(name = "2. Escuchando - Oscuro", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun ListeningDarkPreview() {
    BusAlertTheme(isDark = true) {
        ListeningScreen(prompt = "¿Dónde estás?", isEnglish = false)
    }
}

@Preview(name = "3. Resultado - Oscuro", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun ResultDarkPreview() {
    BusAlertTheme(isDark = true) {
        ResultScreen(
            result = NavigationInstruction(
                spokenText = "Take route E1 from Avenida Boyacá",
                displayTitle = "Route E1",
                displaySubtitle = "Av. Boyacá Cll 22 • 8 min",
                estimatedMinutes = 8,
                recommendedRouteId = "E1"
            ),
            isEnglish = true,
            onDismiss = {}
        )
    }
}

@Preview(name = "4. Resultado - Claro", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun ResultLightPreview() {
    BusAlertTheme(isDark = false) {
        ResultScreen(
            result = NavigationInstruction(
                spokenText = "Toma la ruta E1 desde la Avenida Boyacá",
                displayTitle = "Ruta E1",
                displaySubtitle = "Av. Boyacá Cll 22 • 8 min",
                estimatedMinutes = 8,
                recommendedRouteId = "E1"
            ),
            isEnglish = false,
            onDismiss = {}
        )
    }
}

@Preview(name = "5. Teclado - Oscuro", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun TypingDarkPreview() {
    BusAlertTheme(isDark = true) {
        TypingScreen(
            prompt = "¿A dónde vas?",
            placeholder = "Ej: Lorenzo de Aldana",
            inputText = "Lorenzo",
            onTextChange = {},
            onSubmit = {},
            onCancel = {}
        )
    }
}

@Preview(name = "5b. Teclado - Claro", device = "id:wearos_large_round", showSystemUi = true)
@Composable
private fun TypingLightPreview() {
    BusAlertTheme(isDark = false) {
        TypingScreen(
            prompt = "¿A dónde vas?",
            placeholder = "Ej: Lorenzo de Aldana",
            inputText = "UCC",
            onTextChange = {},
            onSubmit = {},
            onCancel = {}
        )
    }
}

// Refactor: Optimizacion de paddings dinamicos y adaptacion de resolucion circular
