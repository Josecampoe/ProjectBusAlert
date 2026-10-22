package com.busalert.mobile.ui.routes

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.busalert.mobile.R
import com.busalert.mobile.theme.AppTheme
import com.busalert.shared.domain.RouteDirection
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState

/**
 * Mapa del recorrido. Solo dibuja paradas con coordenadas y avisa cuando
 * faltan (los datos reales aún no las tienen) o cuando no hay clave de Maps.
 */
@Composable
fun RouteMap(direction: RouteDirection, mapsEnabled: Boolean) {
    val located = direction.locatedStops
    val dimens = AppTheme.dimens
    when {
        !mapsEnabled -> MapNotice(stringResource(R.string.map_not_configured))
        located.isEmpty() -> MapNotice(stringResource(R.string.map_no_coordinates))
        else -> {
            val points = located.map { LatLng(it.stop.latitude!!, it.stop.longitude!!) }
            val camera = rememberCameraPositionState()
            val lineColor = MaterialTheme.colorScheme.primary
            val paddingPx = with(LocalDensity.current) { dimens.xl.roundToPx() }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding),
            ) {
                GoogleMap(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(dimens.mapHeight),
                    cameraPositionState = camera,
                    onMapLoaded = {
                        val bounds = points.fold(LatLngBounds.builder()) { b, p -> b.include(p) }.build()
                        camera.move(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
                    },
                ) {
                    Polyline(points = points, color = lineColor)
                    located.zip(points).forEach { (routeStop, point) ->
                        Marker(state = MarkerState(point), title = routeStop.stop.name)
                    }
                }
            }
            if (located.size < direction.stops.size) {
                MapNotice(
                    pluralStringResource(
                        R.plurals.map_partial_coordinates,
                        direction.stops.size,
                        located.size,
                        direction.stops.size,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MapNotice(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.screenPadding, vertical = AppTheme.dimens.s),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
