package com.busalert.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.busalert.wear.R
import com.busalert.wear.theme.BusAlertTheme
import com.busalert.wear.theme.routeColor

/** Luminancia a partir de la cual el texto sobre el color de la ruta va oscuro. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

/** Insignia con el código de la ruta, en el color definido en los datos (o el primario). */
@Composable
fun RouteBadge(code: String, colorHex: String?, modifier: Modifier = Modifier) {
    val background = routeColor(colorHex)
    val colors = BusAlertTheme.colors
    val content = if (background.luminance() > LIGHT_BACKGROUND_LUMINANCE) colors.background else colors.onPrimary
    val description = stringResource(R.string.cd_route_badge, code)
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = BusAlertTheme.dimens.routeBadgeMinWidth)
            .background(background, BusAlertTheme.shapes.small)
            .padding(horizontal = BusAlertTheme.spacing.m, vertical = BusAlertTheme.spacing.xxs)
            .clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bus),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(BusAlertTheme.dimens.iconSmall),
        )
        Text(text = code, style = BusAlertTheme.typography.button, color = content)
    }
}

/** Tarjeta informativa con el borde y la superficie del tema. */
@Composable
fun InfoCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val colors = BusAlertTheme.colors
    val shape = BusAlertTheme.shapes.large
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(BusAlertTheme.dimens.outline, colors.outline, shape)
            .padding(horizontal = BusAlertTheme.spacing.l, vertical = BusAlertTheme.spacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs),
    ) {
        content()
    }
}

/** Línea de detalle (ícono + texto) dentro de una tarjeta. */
@Composable
fun DetailLine(text: String, iconRes: Int? = null, emphasized: Boolean = false) {
    val colors = BusAlertTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs, Alignment.CenterHorizontally),
    ) {
        iconRes?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(BusAlertTheme.dimens.iconSmall),
            )
        }
        Text(
            text = text,
            style = if (emphasized) BusAlertTheme.typography.title3 else BusAlertTheme.typography.caption1,
            color = if (emphasized) colors.onSurface else colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Aviso destacado de datos de demostración (para que nadie los tome como reales). */
@Composable
fun DemoBadge() {
    val colors = BusAlertTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BusAlertTheme.spacing.xs),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = colors.demo,
            modifier = Modifier.size(BusAlertTheme.dimens.iconSmall),
        )
        Text(
            text = stringResource(R.string.result_demo_badge),
            style = BusAlertTheme.typography.caption2,
            color = colors.demo,
        )
    }
}
