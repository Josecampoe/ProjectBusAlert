package com.busalert.wear.voice

import android.content.Context
import android.speech.SpeechRecognizer

class VoiceRecognitionManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun isRecognitionAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}
