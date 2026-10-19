package com.busalert.wear.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Administrador de reconocimiento de voz 100% nativo dentro de la app (In-App STT).
 * Evita por completo la pantalla negra del sistema y mantiene la interfaz personalizada.
 */
class VoiceRecognitionManager(
    private val context: Context,
    private val onReady: () -> Unit = {},
    private val onRmsChanged: (Float) -> Unit = {},
    private val onPartialResult: (String) -> Unit = {},
    private val onResult: (String) -> Unit = {},
    private val onError: (String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    fun startListening(isEnglish: Boolean = false) {
        mainHandler.post {
            stopListening()

            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                onError("El servicio de voz no está disponible en este dispositivo.")
                return@post
            }

            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            isListening = true
                            onReady()
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {
                            if (isListening) {
                                onRmsChanged(rmsdB)
                            }
                        }

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            isListening = false
                        }

                        override fun onError(error: Int) {
                            isListening = false
                            val message = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "No se entendieron palabras."
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No se detectó voz a tiempo."
                                SpeechRecognizer.ERROR_AUDIO -> "Error al capturar audio del micrófono."
                                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Falta permiso de micrófono."
                                SpeechRecognizer.ERROR_NETWORK,
                                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Sin conexión al servicio de voz."
                                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Micrófono ocupado."
                                else -> "No se pudo reconocer la voz."
                            }
                            onError(message)
                        }

                        override fun onResults(results: Bundle?) {
                            isListening = false
                            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            val text = matches?.firstOrNull()?.trim()
                            if (!text.isNullOrEmpty()) {
                                onResult(text)
                            } else {
                                onError("No se escuchó ninguna palabra.")
                            }
                        }

                        override fun onPartialResults(partialResults: Bundle?) {
                            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            matches?.firstOrNull()?.let { onPartialResult(it) }
                        }

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    })
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isEnglish) "en-US" else "es-CO")
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, if (isEnglish) "en-US" else "es-CO")
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }

                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                isListening = false
                onError("No se pudo iniciar el micrófono: ${e.localizedMessage ?: "error"}")
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                if (isListening) {
                    speechRecognizer?.stopListening()
                    isListening = false
                }
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
