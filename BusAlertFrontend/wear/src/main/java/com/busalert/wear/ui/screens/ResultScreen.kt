package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Text
import com.busalert.shared.domain.TripOption
import com.busalert.shared.domain.TripPlan
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.DemoBadge
import com.busalert.wear.ui.components.DetailLine
import com.busalert.wear.ui.components.InfoCard
import com.busalert.wear.ui.components.RouteBadge
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen

/** Alternativas que se muestran debajo de la recomendada (el reloj es pequeño). */
private const val MAX_ALTERNATIVES = 3

/**
 * Resultado: "Te recomiendo esta ruta" con el tiempo aproximado del recorrido,
 * dónde subir y bajar, y debajo las demás opciones. Los minutos son siempre
 * una estimación. El texto de la IA, si llegó, se usa para leer en voz alta.
 */
@Composable
fun ResultScreen(
    plan: TripPlan,
    speakEnabled: Boolean,
    isSpeaking: Boolean,
    onStopVoice: () -> Unit,
    onRepeatVoice: () -> Unit,
    onNewQuery: () -> Unit,
    onHome: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    neighborhood: String? = plan.originNeighborhood,
    volumeMuted: Boolean = false,
) {
    val option = plan.recommended ?: return
    val first = option.firstLeg
    val last = option.legs.last()
    val outOfService = option.legs.any { it.estimate?.withinServiceHours == false }
    val alternatives = plan.alternatives.take(MAX_ALTERNATIVES)

    ScrollableScreen {
        item { SectionLabel(stringResource(R.string.result_recommend)) }
        item { RouteBadges(option) }
        item { ScreenTitle(stringResource(R.string.result_route_title, option.legs.joinToString(" + ") { it.routeCode })) }
        option.estimatedMinutes?.let { minutes ->
            item { DetailLine(stringResource(R.string.result_trip_time, minutes), emphasized = true) }
        }
        item {
            InfoCard {
                neighborhood?.let {
                    DetailLine(stringResource(R.string.result_you_are_in, it), iconRes = R.drawable.ic_location)
                }
                val distance = plan.nearestStopDistanceMeters
                DetailLine(
                    if (distance != null) {
                        stringResource(R.string.result_board_at_distance, first.board.name, distance)
                    } else {
                        stringResource(R.string.result_board_at, first.board.name)
                    },
                    iconRes = R.drawable.ic_bus,
                )
                plan.originPlace?.let {
                    DetailLine(stringResource(R.string.result_board_near_place, it), iconRes = R.drawable.ic_location)
                }
                option.transferSteps.firstOrNull()?.let { step ->
                    if (step.walkMeters > 0) {
                        DetailLine(
                            stringResource(R.string.result_walk_transfer, step.walkMeters, step.to.name),
                            iconRes = R.drawable.ic_walk,
                        )
                    } else {
                        DetailLine(stringResource(R.string.result_transfer_at, step.to.name), iconRes = R.drawable.ic_bus)
                    }
                }
                DetailLine(
                    stringResource(
                        R.string.result_alight_with_stops,
                        last.alight.name,
                        pluralStringResource(R.plurals.result_stops_count, option.totalStops, option.totalStops),
                    ),
                )
                plan.destinationPlace?.let {
                    DetailLine(stringResource(R.string.result_walk_to_place, it), iconRes = R.drawable.ic_walk)
                }
                if (outOfService) {
                    DetailLine(stringResource(R.string.result_out_of_service), iconRes = R.drawable.ic_warning)
                }
            }
        }
        if (plan.isDemoData) item { DemoBadge() }
        item {
            ActionChip(
                label = stringResource(if (isFavorite) R.string.action_saved_favorite else R.string.action_save_favorite),
                onClick = onToggleFavorite,
                icon = if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_border,
                style = if (isFavorite) ChipStyle.SECONDARY else ChipStyle.PRIMARY,
            )
        }
        if (speakEnabled && volumeMuted) {
            item { DetailLine(stringResource(R.string.result_volume_muted), iconRes = R.drawable.ic_volume_off) }
        }
        if (speakEnabled) {
            item {
                if (isSpeaking) {
                    ActionChip(
                        stringResource(R.string.action_stop_voice),
                        onStopVoice,
                        icon = R.drawable.ic_volume_off,
                        style = ChipStyle.SECONDARY,
                    )
                } else {
                    ActionChip(
                        stringResource(R.string.action_repeat_voice),
                        onRepeatVoice,
                        icon = R.drawable.ic_volume_up,
                        style = ChipStyle.SECONDARY,
                    )
                }
            }
        }
        if (alternatives.isNotEmpty()) {
            item { SectionLabel(stringResource(R.string.result_other_options)) }
            alternatives.forEach { alternative -> item { AlternativeCard(alternative) } }
        }
        item { ActionChip(stringResource(R.string.action_new_query), onNewQuery, icon = R.drawable.ic_mic) }
        item { ActionChip(stringResource(R.string.action_home), onHome, style = ChipStyle.SECONDARY) }
    }
}

/** Encabezado pequeño de sección ("Te recomiendo esta ruta", "Otras opciones"). */
@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.semantics { heading() },
        style = BusAlertTheme.typography.caption1,
        color = BusAlertTheme.colors.accent,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun RouteBadges(option: TripOption) {
    Row(horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs)) {
        option.legs.forEach { RouteBadge(code = it.routeCode, colorHex = it.routeColor) }
    }
}

/** Otra opción, resumida: rutas, minutos aproximados, dónde subir y transbordos. */
@Composable
private fun AlternativeCard(option: TripOption) {
    val board = option.firstLeg.board.name
    InfoCard {
        RouteBadges(option)
        DetailLine(
            option.estimatedMinutes?.let { stringResource(R.string.result_alt_summary, it, board) }
                ?: stringResource(R.string.result_board_at, board),
        )
        if (option.transfers > 0) {
            DetailLine(
                pluralStringResource(R.plurals.result_transfers_count, option.transfers, option.transfers),
                iconRes = R.drawable.ic_walk,
            )
        }
    }
}
