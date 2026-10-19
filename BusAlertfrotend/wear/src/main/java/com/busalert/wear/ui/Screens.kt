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

@Composable
// Controles circulares de cancelacion y reintento
@Composable
fun ListeningScreenControls(onCancel: () -> Unit, onRetry: () -> Unit) {}

@Composable
fun ListeningScreen(
    currentText: String,
    onCancel: () -> Unit
) {
    Text("Escuchando...")
}

@Composable
fun TypingScreen(
    onSend: (String) -> Unit,
    onCancel: () -> Unit
) {
    Text("Teclado BusAlert")
}

// Alternancia fluida e instantanea entre voz y teclado
@Composable
fun InputSwitchRow(onSwitchToVoice: () -> Unit, onSwitchToType: () -> Unit) {}

@Composable
fun ResultScreen(
    result: String,
    onBack: () -> Unit
) {
    Text("Resultado: $result")
}
