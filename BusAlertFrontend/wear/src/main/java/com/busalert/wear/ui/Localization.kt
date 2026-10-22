package com.busalert.wear.ui

import android.content.res.Configuration
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
    val localizedContext = remember(configuration) { baseContext.createConfigurationContext(configuration) }
    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
        content = content,
    )
}

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
}
