package com.busalert.mobile.ui.routes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.busalert.mobile.R
import com.busalert.mobile.theme.AppTheme
import com.busalert.mobile.ui.components.DemoLabel
import com.busalert.mobile.ui.components.EmptyMessage
import com.busalert.mobile.ui.components.FavoriteButton
import com.busalert.mobile.ui.components.LoadContent
import com.busalert.mobile.ui.components.RouteBadge
import com.busalert.mobile.ui.components.SearchField
import com.busalert.shared.domain.RouteDirection
import com.busalert.shared.domain.RouteSummary

/** Lista de rutas con búsqueda y favoritos. */
@Composable
fun RoutesScreen(viewModel: RoutesViewModel, onOpenRoute: (RouteSummary) -> Unit) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.routes.collectAsState()
    val favorites by viewModel.favoriteCodes.collectAsState()

    Column(Modifier.fillMaxSize()) {
        SearchField(query, viewModel::onQueryChange, stringResource(R.string.routes_search_hint))
        LoadContent(state, onRetry = viewModel::refresh) { routes ->
            when {
                routes.isEmpty() && query.isNotBlank() -> EmptyMessage(stringResource(R.string.routes_no_match))
                routes.isEmpty() -> EmptyMessage(stringResource(R.string.routes_empty))
                else -> LazyColumn {
                    items(routes, key = { it.code }) { route ->
                        RouteRow(
                            route = route,
                            isFavorite = route.code in favorites,
                            onToggleFavorite = { viewModel.toggleFavorite(route) },
                            onClick = { onOpenRoute(route) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRow(route: RouteSummary, isFavorite: Boolean, onToggleFavorite: () -> Unit, onClick: () -> Unit) {
    val endpoints = if (route.origin != null && route.destination != null) {
        stringResource(R.string.route_endpoints, route.origin!!, route.destination!!)
    } else {
        pluralStringResource(R.plurals.route_directions_count, route.directionCount, route.directionCount)
    }
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = { RouteBadge(route.code, route.colorHex) },
        headlineContent = { Text(route.name) },
        supportingContent = {
            Column {
                Text(endpoints)
                if (!route.verified) Text(stringResource(R.string.route_unverified), color = MaterialTheme.colorScheme.outline)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (route.isDemo) DemoLabel()
                FavoriteButton(isFavorite, onToggleFavorite)
            }
        },
    )
}

/** Detalle de una ruta: sentido, mapa y paradas en orden. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(viewModel: RouteDetailViewModel, routeName: String, mapsEnabled: Boolean, onBack: () -> Unit) {
    val state by viewModel.directions.collectAsState()
    val selected by viewModel.selectedDirection.collectAsState()
    val isFavorite by viewModel.isFavorite.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(routeName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = { FavoriteButton(isFavorite) { viewModel.toggleFavorite(routeName) } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            LoadContent(state, onRetry = viewModel::refresh) { directions ->
                if (directions.isEmpty()) {
                    EmptyMessage(stringResource(R.string.route_no_stops))
                } else {
                    val index = selected.coerceIn(directions.indices)
                    DirectionContent(directions, index, viewModel::selectDirection, mapsEnabled)
                }
            }
        }
    }
}

@Composable
private fun DirectionContent(
    directions: List<RouteDirection>,
    selected: Int,
    onSelect: (Int) -> Unit,
    mapsEnabled: Boolean,
) {
    val direction = directions[selected]
    if (directions.size > 1) {
        TabRow(selectedTabIndex = selected) {
            directions.forEachIndexed { i, d -> Tab(selected = i == selected, onClick = { onSelect(i) }, text = { Text(d.name) }) }
        }
    }
    LazyColumn {
        item { RouteMap(direction, mapsEnabled) }
        item {
            Text(
                text = stringResource(R.string.route_detail_stops),
                modifier = Modifier
                    .padding(AppTheme.dimens.screenPadding)
                    .semantics { heading() },
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (direction.stops.isEmpty()) item { EmptyMessage(stringResource(R.string.route_no_stops)) }
        items(direction.stops, key = { "${direction.key}-${it.order}" }) { routeStop ->
            ListItem(
                leadingContent = {
                    Text(
                        text = stringResource(R.string.route_stop_order, routeStop.order),
                        modifier = Modifier.width(AppTheme.dimens.stopOrderWidth),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.End,
                    )
                },
                headlineContent = { Text(routeStop.stop.name) },
                supportingContent = routeStop.stop.externalId?.let { { Text(it) } },
            )
        }
    }
}
