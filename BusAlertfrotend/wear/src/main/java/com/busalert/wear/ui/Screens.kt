package com.busalert.wear.ui

import androidx.compose.runtime.Composable
import androidx.wear.compose.material.Text

@Composable
fun HomeScreen(
    onVoiceClick: () -> Unit,
    onTypeClick: () -> Unit
) {
    Text("BusAlert Wear OS")
}
