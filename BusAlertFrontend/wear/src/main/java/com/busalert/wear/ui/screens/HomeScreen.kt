package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Text
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.MicButton
import com.busalert.wear.ui.components.ScrollableScreen

/** Inicio: marca, botón de micrófono y acceso a ajustes. */
@Composable
fun HomeScreen(onStartQuery: () -> Unit, onOpenSettings: () -> Unit) {
    val colors = BusAlertTheme.colors
    ScrollableScreen {
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xxs),
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    modifier = Modifier.semantics { heading() },
                    style = BusAlertTheme.typography.title2,
                    color = colors.accent,
                )
                Text(
                    text = stringResource(R.string.home_city),
                    style = BusAlertTheme.typography.caption2,
                    color = colors.onSurfaceMuted,
                )
            }
        }
        item { MicButton(contentDescription = stringResource(R.string.home_start_query), onClick = onStartQuery) }
        item { BodyText(stringResource(R.string.home_hint), muted = true) }
        item {
            ActionChip(
                label = stringResource(R.string.action_settings),
                onClick = onOpenSettings,
                icon = R.drawable.ic_settings,
                style = ChipStyle.SECONDARY,
            )
        }
    }
}
