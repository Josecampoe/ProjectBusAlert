package com.busalert.wear.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceSpeaker(context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isReady = false
    private var currentIsEnglish: Boolean = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            applyLanguage(currentIsEnglish)
            isReady = true
        }
    }

    fun setLanguage(isEnglish: Boolean) {
        currentIsEnglish = isEnglish
        if (isReady) {
            applyLanguage(isEnglish)
        }
    }

    private fun applyLanguage(isEnglish: Boolean) {
        if (isEnglish) {
            tts?.language = Locale.US
        } else {
            val colombiaLocale = Locale("es", "CO")
            val result = tts?.setLanguage(colombiaLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("es", "ES"))
            }
        }
    }

    fun speak(text: String) {
        if (isReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "busalert_voice")
        }
    }

    fun stop() {
        tts?.stop()
        tts?.shutdown()
    }
}
