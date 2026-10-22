package com.busalert.mobile.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import com.busalert.mobile.R
import com.busalert.mobile.theme.AppTheme
import com.busalert.shared.domain.AppLanguage

/** Estado del backend tal como lo ve la app (para mostrarlo en ajustes). */
enum class BackendMode { CONFIGURED, DEMO, MISSING }

/** Ajustes sincronizados con el reloj. */
@Composable
fun SettingsScreen(viewModel: UserDataViewModel, backendMode: BackendMode) {
    val preferences by viewModel.preferences.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_language)) },
            supportingContent = {
                Row {
                    LanguageChip(AppLanguage.SPANISH, R.string.language_spanish, preferences.language, viewModel::setLanguage)
                    LanguageChip(AppLanguage.ENGLISH, R.string.language_english, preferences.language, viewModel::setLanguage)
                }
            },
        )
        SwitchRow(stringResource(R.string.settings_dark_theme), preferences.darkTheme, viewModel::setDarkTheme)
        SwitchRow(stringResource(R.string.settings_speak_results), preferences.speakResults, viewModel::setSpeakResults)
        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_backend)) },
            supportingContent = {
                when (backendMode) {
                    BackendMode.DEMO -> Text(stringResource(R.string.settings_backend_demo))
                    BackendMode.MISSING -> Text(stringResource(R.string.settings_backend_missing))
                    BackendMode.CONFIGURED -> Unit
                }
            },
        )
        Text(
            text = stringResource(R.string.settings_sync_note),
            modifier = Modifier.padding(AppTheme.dimens.screenPadding),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LanguageChip(language: AppLanguage, labelRes: Int, current: AppLanguage, onSelect: (AppLanguage) -> Unit) {
    FilterChip(
        selected = current == language,
        onClick = { onSelect(language) },
        label = { Text(stringResource(labelRes)) },
        modifier = Modifier.padding(end = AppTheme.dimens.s),
    )
}

/** Fila completa táctil con interruptor (TalkBack la anuncia como un interruptor). */
@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.toggleable(value = checked, onValueChange = onChange, role = Role.Switch),
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
    )
}
