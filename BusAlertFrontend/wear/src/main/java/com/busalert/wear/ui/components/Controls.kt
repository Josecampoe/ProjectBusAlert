package com.busalert.wear.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import com.busalert.wear.theme.BusAlertTheme

enum class ChipStyle { PRIMARY, SECONDARY }

/** Líneas máximas de la etiqueta de un chip antes de recortar con "…". */
private const val CHIP_LABEL_MAX_LINES = 2
private const val SECONDARY_LABEL_MAX_LINES = 1

/** Acción principal de una pantalla, a lo ancho y con altura táctil de Wear (≥ 52 dp). */
@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    style: ChipStyle = ChipStyle.PRIMARY,
    secondaryLabel: String? = null,
) {
    val colors = BusAlertTheme.colors
    Chip(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        label = { Text(text = label, maxLines = CHIP_LABEL_MAX_LINES, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = secondaryLabel?.let { { Text(text = it, maxLines = SECONDARY_LABEL_MAX_LINES, overflow = TextOverflow.Ellipsis) } },
        icon = icon?.let {
            {
                Icon(
                    painter = painterResource(it),
                    contentDescription = null, // El texto del chip ya describe la acción.
                    modifier = Modifier.size(ChipDefaults.IconSize),
                )
            }
        },
        colors = when (style) {
            ChipStyle.PRIMARY -> ChipDefaults.chipColors(
                backgroundColor = colors.primary,
                contentColor = colors.onPrimary,
                iconColor = colors.onPrimary,
                secondaryContentColor = colors.onPrimary,
            )
            ChipStyle.SECONDARY -> ChipDefaults.chipColors(
                backgroundColor = colors.surfaceVariant,
                contentColor = colors.onSurface,
                iconColor = colors.accent,
                secondaryContentColor = colors.onSurfaceVariant,
            )
        },
    )
}

/** Botón redondo solo con ícono; [contentDescription] es obligatorio para TalkBack. */
@Composable
fun IconActionButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val colors = BusAlertTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.size(ButtonDefaults.DefaultButtonSize),
        colors = if (primary) {
            ButtonDefaults.buttonColors(backgroundColor = colors.primary, contentColor = colors.onPrimary)
        } else {
            ButtonDefaults.buttonColors(backgroundColor = colors.surfaceVariant, contentColor = colors.onSurface)
        },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(ButtonDefaults.DefaultIconSize),
        )
    }
}

/** Ajuste on/off con interruptor; todo el chip es táctil. */
@Composable
fun SettingToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @DrawableRes icon: Int,
) {
    val colors = BusAlertTheme.colors
    ToggleChip(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label, maxLines = CHIP_LABEL_MAX_LINES, overflow = TextOverflow.Ellipsis) },
        appIcon = { Icon(painter = painterResource(icon), contentDescription = null, tint = colors.accent) },
        toggleControl = { Switch(checked = checked, onCheckedChange = null) },
    )
}

/** Título de pantalla (marcado como encabezado para TalkBack). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.semantics { heading() },
        style = BusAlertTheme.typography.title3,
        color = BusAlertTheme.colors.onSurface,
        textAlign = TextAlign.Center,
    )
}

/** Texto de apoyo centrado. */
@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, muted: Boolean = false) {
    val colors = BusAlertTheme.colors
    Text(
        text = text,
        modifier = modifier,
        style = BusAlertTheme.typography.body2,
        color = if (muted) colors.onSurfaceMuted else colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Bloque de título + texto con ícono opcional, para mensajes y avisos. */
@Composable
fun MessageHeader(
    title: String,
    body: String?,
    @DrawableRes icon: Int? = null,
    iconDescription: String? = null,
) {
    val spacing = BusAlertTheme.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        icon?.let {
            Icon(
                painter = painterResource(it),
                contentDescription = iconDescription,
                tint = BusAlertTheme.colors.accent,
                modifier = Modifier.size(BusAlertTheme.dimens.iconLarge),
            )
        }
        ScreenTitle(title)
        body?.let { BodyText(it) }
    }
}
