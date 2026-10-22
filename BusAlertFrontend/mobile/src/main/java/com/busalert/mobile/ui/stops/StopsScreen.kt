package com.busalert.mobile.ui.stops

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.busalert.mobile.R
import com.busalert.mobile.ui.components.DemoLabel
import com.busalert.mobile.ui.components.EmptyMessage
import com.busalert.mobile.ui.components.FavoriteButton
import com.busalert.mobile.ui.components.LoadContent
import com.busalert.mobile.ui.components.SearchField
import com.busalert.shared.domain.StopWithRoutes

/** Búsqueda de paradas con las rutas que pasan por cada una. */
@Composable
fun StopsScreen(viewModel: StopsViewModel) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.results.collectAsState()
    val favorites by viewModel.favoriteStops.collectAsState()

    Column(Modifier.fillMaxSize()) {
        SearchField(query, viewModel::onQueryChange, stringResource(R.string.stops_search_hint))
        when (val current = state) {
            StopSearchState.Idle ->
                EmptyMessage(
                pluralStringResource(
                    R.plurals.stops_prompt,
                    StopSearchDefaults.MIN_QUERY_LENGTH,
                    StopSearchDefaults.MIN_QUERY_LENGTH,
                ),
            )
            is StopSearchState.Searching -> LoadContent(current.load, onRetry = viewModel::retry) { stops ->
                if (stops.isEmpty()) {
                    EmptyMessage(stringResource(R.string.stops_empty))
                } else {
                    LazyColumn {
                        items(stops, key = { it.stop.id }) { stop ->
                            StopRow(stop, stop.stop.name in favorites) { viewModel.toggleFavorite(stop) }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StopRow(stop: StopWithRoutes, isFavorite: Boolean, onToggleFavorite: () -> Unit) {
    val separator = stringResource(R.string.list_separator)
    val routes = stop.routes.map { it.routeCode }.distinct().joinToString(separator)
    ListItem(
        headlineContent = { Text(stop.stop.name) },
        overlineContent = stop.neighborhood?.let { { Text(it) } },
        supportingContent = { Text(stringResource(R.string.stop_routes, routes)) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (stop.isDemo) DemoLabel()
                FavoriteButton(isFavorite, onToggleFavorite)
            }
        },
    )
}
