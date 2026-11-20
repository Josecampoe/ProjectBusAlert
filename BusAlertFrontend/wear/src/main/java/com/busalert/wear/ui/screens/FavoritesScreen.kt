package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.busalert.shared.preferences.Favorite
import com.busalert.shared.preferences.SavedTrip
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.DetailLine
import com.busalert.wear.ui.components.InfoCard
import com.busalert.wear.ui.components.RouteBadge
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen

/** Viajes guardados. Al tocar uno se muestra su ruta guardada, sin consultar ni preguntar. */
@Composable
fun FavoritesScreen(
    favorites: List<Favorite>,
    onOpen: (Favorite) -> Unit,
    onBack: () -> Unit,
) {
    ScrollableScreen {
        item { ScreenTitle(stringResource(R.string.favorites_title)) }
        if (favorites.isEmpty()) {
            item { BodyText(stringResource(R.string.favorites_empty), muted = true) }
        }
        favorites.forEach { favorite ->
            item(key = favorite.id) {
                ActionChip(
                    label = favorite.label,
                    onClick = { onOpen(favorite) },
                    icon = R.drawable.ic_star,
                    secondaryLabel = favorite.trip?.let { summary(it) },
                )
            }
        }
        item {
            ActionChip(stringResource(R.string.action_back), onBack, style = ChipStyle.SECONDARY)
        }
    }
}

/** "C5 · ≈ 15 min" (o solo las rutas si no hay estimación). */
@Composable
private fun summary(trip: SavedTrip): String {
    val routes = trip.routeCodes.joinToString(" + ")
    return trip.estimatedMinutes?.let { stringResource(R.string.favorite_summary, routes, it) } ?: routes
}

/**
 * Ruta guardada de un viaje favorito: de dónde a dónde, en qué bus, dónde
 * subir y bajar y el tiempo aproximado. No consulta al servidor ni usa el GPS.
 */
@Composable
fun FavoriteDetailScreen(
    favorite: Favorite,
    onRemove: () -> Unit,
    onBack: () -> Unit,
) {
    val trip = favorite.trip
    ScrollableScreen {
        item { ScreenTitle(favorite.label) }
        if (trip == null) {
            item { BodyText(stringResource(R.string.favorite_no_details), muted = true) }
        } else {
            if (trip.routeCodes.isNotEmpty()) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs)) {
                        trip.routeCodes.forEach { RouteBadge(code = it, colorHex = null) }
                    }
                }
            }
            trip.estimatedMinutes?.let { minutes ->
                item { DetailLine(stringResource(R.string.result_trip_time, minutes), emphasized = true) }
            }
            item {
                InfoCard {
                    DetailLine(stringResource(R.string.result_board_at, trip.boardStop), iconRes = R.drawable.ic_bus)
                    trip.transferStop?.let {
                        DetailLine(stringResource(R.string.result_transfer_at, it), iconRes = R.drawable.ic_bus)
                    }
                    DetailLine(
                        stringResource(
                            R.string.result_alight_with_stops,
                            trip.alightStop,
                            pluralStringResource(R.plurals.result_stops_count, trip.totalStops, trip.totalStops),
                        ),
                    )
                    if (trip.to != trip.alightStop) {
                        DetailLine(stringResource(R.string.result_walk_to_place, trip.to), iconRes = R.drawable.ic_walk)
                    }
                }
            }
        }
        item {
            ActionChip(
                stringResource(R.string.action_remove_favorite),
                onRemove,
                icon = R.drawable.ic_star_border,
                style = ChipStyle.SECONDARY,
            )
        }
        item { ActionChip(stringResource(R.string.action_back), onBack, style = ChipStyle.SECONDARY) }
    }
}
