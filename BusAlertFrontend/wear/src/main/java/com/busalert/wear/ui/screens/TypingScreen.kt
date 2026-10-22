package com.busalert.wear.ui.screens

import android.app.Activity
import android.app.RemoteInput
import android.content.Intent
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import androidx.wear.input.wearableExtender
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.InfoCard
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen
import com.busalert.wear.ui.messageRes
import com.busalert.wear.voice.VoiceError

/** Clave del campo de texto en el RemoteInput del sistema. */
private const val REMOTE_INPUT_KEY = "busalert_place"

/**
 * Escritura de un lugar con la entrada de texto del sistema (teclado,
 * escritura a mano o dictado), con sugerencias del historial.
 */
@Composable
fun TypingScreen(
    prompt: String,
    text: String,
    recentPlaces: List<String>,
    notice: VoiceError?,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onPickRecent: (String) -> Unit,
    onUseVoice: () -> Unit,
    onCancel: () -> Unit,
) {
    val inputLabel = stringResource(R.string.typing_input_label)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val typed = result.data?.let { RemoteInput.getResultsFromIntent(it) }
            ?.getCharSequence(REMOTE_INPUT_KEY)?.toString()?.trim()
        if (!typed.isNullOrEmpty()) onTextChange(typed)
    }
    val openSystemInput = { launcher.launch(remoteInputIntent(inputLabel)) }

    ScrollableScreen {
        item { ScreenTitle(prompt) }
        notice?.let { item { BodyText(stringResource(it.messageRes())) } }
        item {
            InfoCard {
                Text(
                    text = text.ifBlank { stringResource(R.string.typing_hint) },
                    style = BusAlertTheme.typography.body1,
                    color = if (text.isBlank()) BusAlertTheme.colors.onSurfaceMuted else BusAlertTheme.colors.onSurface,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (text.isNotBlank()) {
            item { ActionChip(stringResource(R.string.action_send), onSubmit, icon = R.drawable.ic_send) }
        }
        item {
            ActionChip(
                label = stringResource(if (text.isBlank()) R.string.action_type else R.string.action_edit),
                onClick = openSystemInput,
                icon = R.drawable.ic_edit,
                style = if (text.isBlank()) ChipStyle.PRIMARY else ChipStyle.SECONDARY,
            )
        }
        if (recentPlaces.isNotEmpty()) {
            item { ListHeader { Text(stringResource(R.string.recent_title)) } }
            recentPlaces.forEach { place ->
                item {
                    ActionChip(place, { onPickRecent(place) }, icon = R.drawable.ic_history, style = ChipStyle.SECONDARY)
                }
            }
        }
        item {
            ActionChip(stringResource(R.string.action_use_voice), onUseVoice, icon = R.drawable.ic_mic, style = ChipStyle.SECONDARY)
        }
        item {
            ActionChip(stringResource(R.string.action_cancel), onCancel, icon = R.drawable.ic_close, style = ChipStyle.SECONDARY)
        }
    }
}

private fun remoteInputIntent(label: String): Intent {
    val remoteInput = RemoteInput.Builder(REMOTE_INPUT_KEY)
        .setLabel(label)
        .wearableExtender {
            setEmojisAllowed(false)
            setInputActionType(EditorInfo.IME_ACTION_SEARCH)
        }
        .build()
    return RemoteInputIntentHelper.createActionRemoteInputIntent().also {
        RemoteInputIntentHelper.putRemoteInputsExtra(it, listOf(remoteInput))
    }
}
