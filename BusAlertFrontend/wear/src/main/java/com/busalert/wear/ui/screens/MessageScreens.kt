package com.busalert.wear.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripStatus
import com.busalert.wear.R
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.MessageHeader
import com.busalert.wear.ui.components.ScrollableScreen
import com.busalert.wear.ui.isRetryable
import com.busalert.wear.ui.messageRes
import com.busalert.wear.ui.noResultMessageRes

/** Error con código estable: ofrece reintentar solo si tiene sentido. */
@Composable
fun ErrorScreen(error: ErrorCode, onRetry: () -> Unit, onHome: () -> Unit) {
    ScrollableScreen {
        item {
            MessageHeader(
                title = stringResource(R.string.error_title),
                body = stringResource(error.messageRes()),
                icon = R.drawable.ic_warning,
                iconDescription = stringResource(R.string.cd_warning),
            )
        }
        if (error.isRetryable) {
            item { ActionChip(stringResource(R.string.action_retry), onRetry, icon = R.drawable.ic_refresh) }
        }
        item {
            ActionChip(
                label = stringResource(R.string.action_home),
                onClick = onHome,
                style = if (error.isRetryable) ChipStyle.SECONDARY else ChipStyle.PRIMARY,
            )
        }
    }
}

/** La consulta llegó al backend pero no hay ruta: explica el motivo. */
@Composable
fun NoResultScreen(status: TripStatus, onNewQuery: () -> Unit, onHome: () -> Unit) {
    ScrollableScreen {
        item {
            MessageHeader(
                title = stringResource(R.string.no_result_title),
                body = stringResource(status.noResultMessageRes()),
                icon = R.drawable.ic_bus,
            )
        }
        item { ActionChip(stringResource(R.string.action_new_query), onNewQuery, icon = R.drawable.ic_mic) }
        item { ActionChip(stringResource(R.string.action_home), onHome, style = ChipStyle.SECONDARY) }
    }
}

/** Explicación previa al diálogo de permiso de ubicación. */
@Composable
fun LocationRationaleScreen(onContinue: () -> Unit, onNotNow: () -> Unit) {
    ScrollableScreen {
        item {
            MessageHeader(
                title = stringResource(R.string.location_rationale_title),
                body = stringResource(R.string.location_rationale_body),
                icon = R.drawable.ic_location,
                iconDescription = stringResource(R.string.cd_location),
            )
        }
        item { ActionChip(stringResource(R.string.action_continue), onContinue) }
        item { ActionChip(stringResource(R.string.action_not_now), onNotNow, style = ChipStyle.SECONDARY) }
    }
}
