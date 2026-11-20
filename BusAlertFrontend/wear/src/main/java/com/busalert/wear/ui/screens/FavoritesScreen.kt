package com.busalert.wear.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.busalert.shared.preferences.Favorite
import com.busalert.wear.R
import com.busalert.wear.ui.components.ActionChip
import com.busalert.wear.ui.components.BodyText
import com.busalert.wear.ui.components.ChipStyle
import com.busalert.wear.ui.components.ScreenTitle
import com.busalert.wear.ui.components.ScrollableScreen

/**
 * Viajes guardados. Al tocar uno se consulta de una vez desde la ubicación
 * actual, sin preguntar nada.
 */
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
                ActionChip(label = favorite.label, onClick = { onOpen(favorite) }, icon = R.drawable.ic_star)
            }
        }
        item {
            ActionChip(stringResource(R.string.action_back), onBack, style = ChipStyle.SECONDARY)
        }
    }
}
