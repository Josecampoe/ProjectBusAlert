package com.busalert.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.busalert.wear.location.FusedLocationSource
import com.busalert.wear.location.hasLocationPermission
import com.busalert.wear.ui.PermissionGate
import com.busalert.wear.ui.WearApp
import com.busalert.wear.voice.ConnectorTripPhraseParser
import com.busalert.wear.voice.VoiceRecognitionManager
import com.busalert.wear.voice.VoiceSpeaker
import kotlinx.coroutines.flow.MutableStateFlow

/** Única Activity: arma las dependencias de plataforma y delega la UI a [WearApp]. */
class MainActivity : ComponentActivity() {

    private val app get() = application as BusAlertWearApp

    private val viewModel: MainWearViewModel by viewModels {
        MainWearViewModel.Factory(
            repository = app.dependencies.tripAssistantRepository,
            transitRepository = app.dependencies.transitRepository,
            locationSource = FusedLocationSource(applicationContext),
            preferencesRepository = app.preferencesRepository,
            phraseParser = ConnectorTripPhraseParser(resources.getStringArray(R.array.trip_destination_connectors).toList()),
        )
    }

    private val partialSpeech = MutableStateFlow("")
    private lateinit var speaker: VoiceSpeaker
    private lateinit var recognizer: VoiceRecognitionManager

    private val permissions = object : PermissionGate {
        override fun hasMicrophone() = ContextCompat.checkSelfPermission(
            this@MainActivity,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        override fun hasLocation() = hasLocationPermission()

        override fun shouldExplainLocation() =
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        speaker = VoiceSpeaker(this)
        recognizer = VoiceRecognitionManager(
            context = this,
            onReady = { partialSpeech.value = "" },
            onPartialResult = { partialSpeech.value = it },
            onResult = {
                partialSpeech.value = it
                viewModel.onSpeechResult(it)
            },
            onError = viewModel::onVoiceError,
        )
        setContent {
            WearApp(
                viewModel = viewModel,
                voiceInput = recognizer,
                voiceOutput = speaker,
                partialSpeech = partialSpeech,
                permissions = permissions,
            )
        }
    }

    override fun onDestroy() {
        recognizer.stop()
        speaker.shutdown()
        super.onDestroy()
    }
}
