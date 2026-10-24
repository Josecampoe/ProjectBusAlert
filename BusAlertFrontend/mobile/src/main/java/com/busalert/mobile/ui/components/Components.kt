package com.busalert.mobile.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.busalert.mobile.R
import com.busalert.mobile.theme.AppTheme
import com.busalert.mobile.theme.parseHexColor
import com.busalert.mobile.ui.LoadState
import com.busalert.mobile.ui.messageRes

/** Luminancia a partir de la cual el texto sobre el color de la ruta va oscuro. */
private const val LIGHT_BACKGROUND_LUMINANCE = 0.5f

/** Muestra carga, error con reintento o el contenido de un [LoadState]. */
@Composable
fun <T> LoadContent(state: LoadState<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        LoadState.Loading -> CenteredMessage {
            CircularProgressIndicator()
            Text(stringResource(R.string.loading), style = MaterialTheme.typography.bodyMedium)
        }
        is LoadState.Failed -> CenteredMessage {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Text(
                text = stringResource(state.error.messageRes()),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        is LoadState.Ready -> content(state.value)
    }
}

@Composable
fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(AppTheme.dimens.xl),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.m),
        ) { content() }
    }
}

@Composable
fun EmptyMessage(text: String) = CenteredMessage {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/** Campo de búsqueda con botón para limpiar. */
@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.screenPadding, vertical = AppTheme.dimens.s),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = null)
                }
            }
        },
        singleLine = true,
    )
}

/** Insignia con el código de ruta en el color definido en los datos (o el primario). */
@Composable
fun RouteBadge(code: String, colorHex: String?) {
    val background = parseHexColor(colorHex) ?: MaterialTheme.colorScheme.primary
    val content = if (background.luminance() > LIGHT_BACKGROUND_LUMINANCE) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(color = background, shape = MaterialTheme.shapes.small) {
        Text(
            text = code,
            modifier = Modifier
                .defaultMinSize(minWidth = AppTheme.dimens.routeBadgeMinWidth)
                .padding(horizontal = AppTheme.dimens.s, vertical = AppTheme.dimens.xs),
            color = content,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
        )
    }
}

/** Etiqueta "Demo" para datos que no son reales. */
@Composable
fun DemoLabel() {
    val description = stringResource(R.string.demo_notice)
    Text(
        text = stringResource(R.string.demo_badge),
        modifier = Modifier.semantics { contentDescription = description },
        style = MaterialTheme.typography.labelMedium,
        color = AppTheme.extraColors.demo,
    )
}

/** Estrella de favorito con descripción accesible según el estado. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = stringResource(
                if (isFavorite) R.string.action_remove_favorite else R.string.action_add_favorite,
            ),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
    }
}

/** Fila con ícono de aviso (por ejemplo, datos de demostración). */
@Composable
fun NoticeRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppTheme.dimens.screenPadding, vertical = AppTheme.dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.s),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AppTheme.extraColors.demo)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
