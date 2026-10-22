package com.busalert.wear.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.busalert.shared.preferences.UserPreferences
import com.busalert.wear.R
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen
import com.busalert.wear.ui.components.SettingToggle

/** Ajustes guardados en DataStore: idioma, tema, lectura en voz alta e historial. */
@Composable
fun SettingsScreen(
    preferences: UserPreferences,
    onToggleLanguage: () -> Unit,
    onDarkThemeChange: (Boolean) -> Unit,
    onSpeakResultsChange: (Boolean) -> Unit,
    onClearHistory: () -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen {
        item { ScreenTitle(stringResource(R.string.settings_title)) }
        item {
            ActionChip(
                label = stringResource(R.string.settings_language),
                // La app ya está en el idioma elegido: muestra "Español" o "English".
                secondaryLabel = stringResource(R.string.settings_language_value),
                onClick = onToggleLanguage,
                style = ChipStyle.SECONDARY,
            )
        }
        item {
            SettingToggle(
                label = stringResource(R.string.settings_dark_theme),
                checked = preferences.darkTheme,
                onCheckedChange = onDarkThemeChange,
                icon = R.drawable.ic_dark_mode,
            )
        }
        item {
            SettingToggle(
                label = stringResource(R.string.settings_speak_results),
                checked = preferences.speakResults,
                onCheckedChange = onSpeakResultsChange,
                icon = R.drawable.ic_volume_up,
            )
        }
        item {
            ActionChip(
                label = stringResource(R.string.settings_clear_history),
                onClick = onClearHistory,
                icon = R.drawable.ic_history,
                style = ChipStyle.SECONDARY,
            )
        }
        item { ActionChip(label = stringResource(R.string.action_back), onClick = onBack) }
    }
}

