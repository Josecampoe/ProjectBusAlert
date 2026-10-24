package com.busalert.mobile.ui

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
import com.busalert.mobile.R
import com.busalert.shared.domain.AppLanguage
import com.busalert.shared.domain.ErrorCode
import java.util.Locale

/**
 * Aplica el idioma elegido en la app (sincronizado con el reloj) sin perder la
 * Activity en la cadena de contextos (ver docs/DECISIONES.md, D-031).
 */
@Composable
fun ProvideAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val baseContext = LocalContext.current
    val baseConfiguration = LocalConfiguration.current
    val configuration = remember(language, baseConfiguration) {
        Configuration(baseConfiguration).apply { setLocale(Locale.forLanguageTag(language.code)) }
    }
    val localized = remember(baseContext, configuration) {
        LocalizedContext(baseContext, baseContext.createConfigurationContext(configuration).resources)
    }
    CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration, content = content)
}

private class LocalizedContext(base: Context, private val localizedResources: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localizedResources
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
