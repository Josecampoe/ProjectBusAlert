package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material.CircularProgressIndicator
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.CenteredScreen
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.ScreenTitle

@Composable
private fun Spinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(BusAlertTheme.dimens.progress),
        indicatorColor = BusAlertTheme.colors.accent,
        trackColor = BusAlertTheme.colors.outline,
        strokeWidth = BusAlertTheme.dimens.progressStroke,
    )
}

/** Consultando el backend. */
@Composable
fun ProcessingScreen(onCancel: () -> Unit) {
    CenteredScreen {
        Spinner()
        ScreenTitle(stringResource(R.string.processing_title))
        BodyText(stringResource(R.string.processing_subtitle), muted = true)
        ActionChip(stringResource(R.string.action_cancel), onCancel, icon = R.drawable.ic_close, style = ChipStyle.SECONDARY)
    }
}

/** Esperando la ubicación del GPS, con salida a "decir dónde estoy". */
@Composable
fun LocatingScreen(onUseVoice: () -> Unit) {
    CenteredScreen {
        Spinner()
        ScreenTitle(stringResource(R.string.locating_title))
        ActionChip(stringResource(R.string.action_say_location), onUseVoice, icon = R.drawable.ic_mic, style = ChipStyle.SECONDARY)
    }
}
