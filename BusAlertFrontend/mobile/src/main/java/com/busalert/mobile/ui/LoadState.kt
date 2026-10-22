package com.busalert.mobile.ui

import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import java.text.Normalizer

/** Estado de una carga remota. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data class Failed(val error: ErrorCode) : LoadState<Nothing>
    data class Ready<T>(val value: T) : LoadState<T>
}

fun <T> Outcome<T>.toLoadState(): LoadState<T> = when (this) {
    is Outcome.Success -> LoadState.Ready(value)
    is Outcome.Failure -> LoadState.Failed(error)
}

private val DIACRITICS = Regex("\\p{Mn}+")

/** Minúsculas y sin tildes, para filtrar listas sin importar cómo se escriba. */
fun String.normalizedForSearch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(DIACRITICS, "").lowercase().trim()
