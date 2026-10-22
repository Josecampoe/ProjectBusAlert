package com.busalert.wear.ui.components

import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme

/** Halos que laten detrás del botón de micrófono (decorativos). */
private const val HALO_COUNT = 3

/** Botón central de micrófono con halos animados. */
@Composable
fun MicButton(contentDescription: String, onClick: () -> Unit) {
    val dimens = BusAlertTheme.dimens
    val motion = BusAlertTheme.motion
    val effects = BusAlertTheme.effects
    val colors = BusAlertTheme.colors
    val transition = rememberInfiniteTransition(label = "mic-halo")

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(dimens.micHalo)) {
        repeat(HALO_COUNT) { index ->
            val spec = infiniteRepeatable<Float>(
                animation = tween(motion.pulseMs, index * motion.pulseStaggerMs, EaseOut),
                repeatMode = RepeatMode.Restart,
            )
            val scale by transition.animateFloat(effects.haloStartScale, effects.haloEndScale, spec, "scale$index")
            val alpha by transition.animateFloat(effects.haloStartAlpha, effects.haloEndAlpha, spec, "alpha$index")
            Box(
                modifier = Modifier
                    .size(dimens.micHalo)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = alpha)),
            )
        }
        Button(
            onClick = onClick,
            modifier = Modifier.size(dimens.micButton),
            colors = ButtonDefaults.buttonColors(backgroundColor = colors.primary, contentColor = colors.onPrimary),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_mic),
                contentDescription = contentDescription,
                modifier = Modifier.size(dimens.iconLarge),
            )
        }
    }
}

/** Barras de audio animadas mientras se escucha. */
@Composable
fun AudioWaveBars(contentDescription: String) {
    val dimens = BusAlertTheme.dimens
    val motion = BusAlertTheme.motion
    val colors = BusAlertTheme.colors
    val transition = rememberInfiniteTransition(label = "audio-wave")
    val center = motion.waveDelaysMs.size / 2

    Row(
        modifier = Modifier
            .height(dimens.waveBarMaxHeight)
            .semantics { this.contentDescription = contentDescription },
        horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        motion.waveDelaysMs.forEachIndexed { index, delay ->
            val fraction by transition.animateFloat(
                initialValue = BusAlertTheme.effects.waveMinFraction,
                targetValue = BusAlertTheme.effects.waveMaxFraction,
                animationSpec = infiniteRepeatable(
                    animation = tween(motion.waveBaseMs + index * motion.waveStepMs, delay, EaseInOutQuad),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "bar$index",
            )
            Box(
                modifier = Modifier
                    .width(dimens.waveBarWidth)
                    .height(dimens.waveBarMaxHeight * fraction)
                    .clip(CircleShape)
                    .background(if (index == center) colors.accent else colors.primary),
            )
        }
    }
}
