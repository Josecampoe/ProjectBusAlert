package com.busalert.wear.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.TripStatus
import com.busalert.wear.R
import com.busalert.wear.voice.VoiceError
import java.util.Locale

/**
 * Aplica el idioma elegido dentro de la app (independiente del idioma del
 * sistema) para que `stringResource` lea `values/` o `values-en/`.
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(language, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(language.code)) }
    }
    val localizedContext = remember(baseContext, configuration) {
        LocalizedContext(baseContext, baseContext.createConfigurationContext(configuration).resources)
    }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}

/**
 * Envuelve el contexto original (la Activity) y solo cambia los recursos.
 * Así se conserva la cadena de contextos que usan los permisos y
 * `rememberLauncherForActivityResult` para encontrar la Activity.
 */
private class LocalizedContext(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
}

/** Mensaje para cada código de error estable (del backend o del cliente). */
@StringRes
fun ErrorCode.messageRes(): Int = when (this) {
    ErrorCode.NETWORK -> R.string.error_network
    ErrorCode.TIMEOUT -> R.string.error_timeout
    ErrorCode.UNAUTHORIZED -> R.string.error_unauthorized
    ErrorCode.RATE_LIMITED -> R.string.error_rate_limited
    ErrorCode.INVALID_REQUEST -> R.string.error_invalid_request
    ErrorCode.NOT_FOUND -> R.string.error_not_found
    ErrorCode.AI_UNAVAILABLE -> R.string.error_ai_unavailable
    ErrorCode.SERVER -> R.string.error_server
    ErrorCode.CONFIGURATION -> R.string.error_configuration
    ErrorCode.UNKNOWN -> R.string.error_unknown
}

/** ¿Tiene sentido ofrecer "Reintentar" para este error? */
val ErrorCode.isRetryable: Boolean
    get() = this in setOf(
        ErrorCode.NETWORK,
        ErrorCode.TIMEOUT,
        ErrorCode.RATE_LIMITED,
        ErrorCode.AI_UNAVAILABLE,
        ErrorCode.SERVER,
        ErrorCode.UNKNOWN,
    )

@StringRes
fun TripStatus.noResultMessageRes(): Int = when (this) {
    TripStatus.NO_ROUTE, TripStatus.OK -> R.string.no_result_no_route
    TripStatus.SAME_PLACE -> R.string.no_result_same_place
    TripStatus.ORIGIN_NOT_FOUND -> R.string.no_result_origin_not_found
    TripStatus.DESTINATION_NOT_FOUND -> R.string.no_result_destination_not_found
    TripStatus.NO_NEARBY_STOPS -> R.string.no_result_no_nearby_stops
}

@StringRes
fun VoiceError.messageRes(): Int = when (this) {
    VoiceError.NO_MATCH -> R.string.voice_error_no_match
    VoiceError.TIMEOUT -> R.string.voice_error_timeout
    VoiceError.AUDIO -> R.string.voice_error_audio
    VoiceError.PERMISSION -> R.string.voice_error_permission
    VoiceError.NETWORK -> R.string.voice_error_network
    VoiceError.BUSY -> R.string.voice_error_busy
    VoiceError.UNAVAILABLE -> R.string.voice_error_unavailable
    VoiceError.GENERIC -> R.string.voice_error_generic
}
