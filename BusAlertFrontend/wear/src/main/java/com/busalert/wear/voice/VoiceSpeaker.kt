package com.busalert.wear.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.busalert.shared.domain.AppLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Lectura en voz alta (TTS) en el idioma de la app. Expone si está hablando
 * para que la UI ofrezca "Detener" y "Repetir".
 */
class VoiceSpeaker(context: Context) : TextToSpeech.OnInitListener, VoiceOutput {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var language: AppLanguage = AppLanguage.SPANISH
    private var lastText: String? = null

    private val _isSpeaking = MutableStateFlow(false)
    override val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        ready = true
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            @Deprecated("Obligatorio de implementar aunque esté obsoleto en la API")
            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                _isSpeaking.value = false
            }
        })
        applyLanguage()
    }

    override fun setLanguage(language: AppLanguage) {
        this.language = language
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val result = tts.setLanguage(Locale.forLanguageTag(SpeechSettings.localeTag(language)))
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Variante regional no instalada: se usa el idioma genérico.
            tts.setLanguage(Locale.forLanguageTag(language.code))
        }
    }

    override fun speak(text: String) {
        lastText = text
        if (ready) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun repeat() {
        lastText?.let(::speak)
    }

    override fun stop() {
        tts.stop()
        _isSpeaking.value = false
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }

    private companion object {
        const val UTTERANCE_ID = "busalert_answer"
    }
}
