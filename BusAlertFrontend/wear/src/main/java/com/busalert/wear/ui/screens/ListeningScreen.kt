package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Text
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.AudioWaveBars
import com.busalert.wear.ui.components.CenteredScreen
import com.busalert.wear.ui.components.IconActionButton
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.messageRes
import com.busalert.wear.voice.VoiceError

/** Máximo de líneas del texto parcial reconocido. */
private const val PARTIAL_TEXT_MAX_LINES = 2

/** Escucha por voz con transcripción en vivo y acciones de cancelar, repetir y escribir. */
@Composable
fun ListeningScreen(
    prompt: String,
    partialText: String,
    notice: VoiceError?,
    onRetry: () -> Unit,
    onUseKeyboard: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = BusAlertTheme.colors
    CenteredScreen {
        ScreenTitle(prompt)
        AudioWaveBars(contentDescription = stringResource(R.string.listening_in_progress))
        val (text, color) = when {
            partialText.isNotBlank() -> stringResource(R.string.listening_partial, partialText) to colors.accent
            notice != null -> stringResource(notice.messageRes()) to colors.error
            else -> stringResource(R.string.listening_speak_now) to colors.onSurfaceMuted
        }
        Text(
            text = text,
            // TalkBack anuncia los cambios (texto reconocido o error) sin mover el foco.
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = BusAlertTheme.typography.body2,
            color = color,
            textAlign = TextAlign.Center,
            maxLines = PARTIAL_TEXT_MAX_LINES,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.s)) {
            IconActionButton(R.drawable.ic_close, stringResource(R.string.action_cancel), onCancel)
            IconActionButton(R.drawable.ic_refresh, stringResource(R.string.action_retry_voice), onRetry)
            IconActionButton(R.drawable.ic_keyboard, stringResource(R.string.action_use_keyboard), onUseKeyboard, primary = true)
        }
    }
}
