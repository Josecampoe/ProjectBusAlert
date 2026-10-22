package com.busalert.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.busalert.wear.theme.BusAlertTheme
import kotlinx.coroutines.launch

/** Estructura base de toda pantalla: fondo del tema, hora arriba, viñeta e indicador de scroll. */
@Composable
fun ScreenScaffold(
    listState: ScalingLazyListState? = null,
    showTime: Boolean = true,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(BusAlertTheme.colors.backgroundBrush),
        timeText = { if (showTime) TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { listState?.let { PositionIndicator(scalingLazyListState = it) } },
    ) {
        content()
    }
}

/**
 * Pantalla desplazable que se adapta a relojes redondos y cuadrados:
 * los elementos se escalan en los bordes y se puede desplazar con la corona.
 */
@Composable
fun ScrollableScreen(content: ScalingLazyListScope.() -> Unit) {
    val listState = rememberScalingLazyListState()
    val spacing = BusAlertTheme.spacing
    ScreenScaffold(listState = listState) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .rotaryScroll(listState),
            autoCentering = AutoCenteringParams(itemIndex = 0),
            contentPadding = PaddingValues(horizontal = spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(spacing.s, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

/** Pantalla fija con el contenido centrado (estados de carga y escucha). */
@Composable
fun CenteredScreen(
    showTime: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spacing = BusAlertTheme.spacing
    ScreenScaffold(showTime = showTime) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.m, Alignment.CenterVertically),
                content = content,
            )
        }
    }
}

/** Desplazamiento con la corona giratoria o el bisel. */
@Composable
private fun Modifier.rotaryScroll(state: ScalingLazyListState): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    return this
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
