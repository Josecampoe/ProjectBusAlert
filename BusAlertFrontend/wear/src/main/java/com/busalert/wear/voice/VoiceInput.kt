package com.busalert.wear.voice

import com.busalert.shared.domain.AppLanguage
import kotlinx.coroutines.flow.StateFlow

/** Lo que la UI necesita del reconocimiento de voz (permite un doble en pruebas y previews). */
interface VoiceInput {
    fun start(language: AppLanguage)
    fun stop()
}

/** Lo que la UI necesita de la lectura en voz alta. */
interface VoiceOutput {
    val isSpeaking: StateFlow<Boolean>
    fun setLanguage(language: AppLanguage)
    fun speak(text: String)
    fun repeat()
    fun stop()
}
