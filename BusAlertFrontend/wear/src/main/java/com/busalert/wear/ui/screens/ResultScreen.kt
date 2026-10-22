package com.busalert.wear.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.busalert.shared.domain.TripPlan
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.DemoBadge
import com.busalert.wear.ui.components.DetailLine
import com.busalert.wear.ui.components.InfoCard
import com.busalert.wear.ui.components.RouteBadge
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen

/**
 * Ruta recomendada. Usa el texto de la IA si llegó; si no, arma el texto con
 * los recursos. Los minutos se presentan siempre como estimación.
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
) {
    val option = plan.recommended ?: return
    val first = option.firstLeg
    val last = option.legs.last()
    val title = plan.message?.displayTitle ?: stringResource(R.string.result_route_title, first.routeCode)
    val subtitle = plan.message?.displaySubtitle ?: stringResource(R.string.result_board_at, first.board.name)
    val outOfService = option.legs.any { it.estimate?.withinServiceHours == false }

    ScrollableScreen {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs)) {
                option.legs.forEach { RouteBadge(code = it.routeCode, colorHex = it.routeColor) }
            }
        }
        item { ScreenTitle(title) }
        item { BodyText(subtitle) }
        item {
            InfoCard {
                option.estimatedMinutes?.let {
                    DetailLine(stringResource(R.string.result_estimate, it), emphasized = true)
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
                plan.nearestStopDistanceMeters?.let {
                    DetailLine(stringResource(R.string.result_nearest_stop, it), iconRes = R.drawable.ic_location)
                }
                if (outOfService) {
                    DetailLine(stringResource(R.string.result_out_of_service), iconRes = R.drawable.ic_warning)
                }
            }
        }
        if (plan.isDemoData) item { DemoBadge() }
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
        item { ActionChip(stringResource(R.string.action_new_query), onNewQuery, icon = R.drawable.ic_mic) }
        item { ActionChip(stringResource(R.string.action_home), onHome, style = ChipStyle.SECONDARY) }
    }
}
