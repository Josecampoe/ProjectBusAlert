package com.busalert.wear.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.busalert.shared.domain.AppLanguage

/** Motivos por los que el reconocimiento de voz no dio texto. La UI los traduce. */
enum class VoiceError {
    NO_MATCH,
    TIMEOUT,
    AUDIO,
    PERMISSION,
    NETWORK,
    BUSY,
    UNAVAILABLE,
    GENERIC,
    ;

    /** Errores en los que conviene pasar directamente al teclado. */
    val suggestsKeyboard: Boolean get() = this == PERMISSION || this == UNAVAILABLE

    companion object {
        fun fromRecognizer(code: Int): VoiceError = when (code) {
            SpeechRecognizer.ERROR_NO_MATCH -> NO_MATCH
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> TIMEOUT
            SpeechRecognizer.ERROR_AUDIO -> AUDIO
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> PERMISSION
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> NETWORK
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> BUSY
            else -> GENERIC
        }
    }
}

/** Configuración del reconocedor y etiquetas de idioma (BCP 47) por idioma de la app. */
object SpeechSettings {
    const val MAX_RESULTS = 3

    fun localeTag(language: AppLanguage): String = when (language) {
        AppLanguage.SPANISH -> "es-CO"
        AppLanguage.ENGLISH -> "en-US"
    }
}

/**
 * Reconocimiento de voz nativo dentro de la app (sin la pantalla del sistema).
 * Informa texto parcial, texto final o un [VoiceError].
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onReady: () -> Unit = {},
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit = {},
    private val onError: (VoiceError) -> Unit = {},
) : VoiceInput {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun start(language: AppLanguage) = startListening(language)

    override fun stop() = stopListening()

    fun startListening(language: AppLanguage) {
        mainHandler.post {
            release()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError(VoiceError.UNAVAILABLE)
                return@post
            }
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(listener)
                    startListening(recognizerIntent(language))
                }
            } catch (e: SecurityException) {
                onError(VoiceError.PERMISSION)
            } catch (e: IllegalStateException) {
                onError(VoiceError.GENERIC)
            }
        }
    }

    fun stopListening() {
        mainHandler.post { release() }
    }

    private fun release() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }

    private fun recognizerIntent(language: AppLanguage): Intent {
        val tag = SpeechSettings.localeTag(language)
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, SpeechSettings.MAX_RESULTS)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = onReady()
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) = onError(VoiceError.fromRecognizer(error))

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
            if (text.isNullOrEmpty()) onError(VoiceError.NO_MATCH) else onResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.let(onPartialResult)
        }
    }
}
