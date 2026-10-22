package com.busalert.mobile.ui.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.busalert.mobile.R
import com.busalert.mobile.theme.AppTheme
import com.busalert.mobile.ui.components.RouteBadge
import com.busalert.mobile.ui.settings.UserDataViewModel
import com.busalert.shared.preferences.FavoriteKind
import com.busalert.shared.preferences.QueryHistoryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Favoritos y consultas recientes (sincronizados con el reloj). */
@Composable
fun HistoryScreen(viewModel: UserDataViewModel) {
    val history by viewModel.history.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
    }

    LazyColumn(Modifier.fillMaxSize()) {
        item { SectionTitle(stringResource(R.string.favorites_title)) }
        if (favorites.isEmpty()) item { Hint(stringResource(R.string.favorites_empty)) }
        items(favorites, key = { "${it.kind}-${it.id}" }) { favorite ->
            ListItem(
                leadingContent = {
                    if (favorite.kind == FavoriteKind.ROUTE) {
                        RouteBadge(favorite.id, colorHex = null)
                    } else {
                        Icon(Icons.Filled.Place, contentDescription = null)
                    }
                },
                headlineContent = { Text(favorite.label) },
                trailingContent = {
                    IconButton(onClick = { viewModel.removeFavorite(favorite) }) {
                        Icon(Icons.Filled.Star, contentDescription = stringResource(R.string.action_remove_favorite))
                    }
                },
            )
        }
        item { SectionTitle(stringResource(R.string.history_title)) }
        if (history.isEmpty()) {
            item { Hint(stringResource(R.string.history_empty)) }
        } else {
            items(history, key = { "${it.timestampMillis}-${it.destination}" }) { entry ->
                HistoryRow(entry, formatter)
                HorizontalDivider()
            }
            item {
                TextButton(onClick = viewModel::clearHistory, modifier = Modifier.padding(AppTheme.dimens.screenPadding)) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Text(stringResource(R.string.history_clear))
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: QueryHistoryEntry, formatter: DateTimeFormatter) {
    val origin = entry.originLabel
    ListItem(
        headlineContent = {
            Text(
                if (origin != null) {
                    stringResource(R.string.history_from_to, origin, entry.destination)
                } else {
                    stringResource(R.string.history_to, entry.destination)
                },
            )
        },
        supportingContent = {
            Text(formatter.format(Instant.ofEpochMilli(entry.timestampMillis).atZone(ZoneId.systemDefault())))
        },
        trailingContent = { entry.routeCodes.firstOrNull()?.let { RouteBadge(it, colorHex = null) } },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .padding(AppTheme.dimens.screenPadding)
            .semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = AppTheme.dimens.screenPadding),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
