package com.busalert.shared.config

/**
 * Configuración que cada app (reloj, móvil) entrega al módulo compartido.
 * Los valores vienen de `BuildConfig`, que a su vez se llena desde
 * `local.properties` o variables de entorno (nunca desde el código).
 */
data class SharedConfig(
    val backendBaseUrl: String,
    val apiKey: String,
    val useMockBackend: Boolean,
    val connectTimeoutMs: Long = NetworkDefaults.CONNECT_TIMEOUT_MS,
    val readTimeoutMs: Long = NetworkDefaults.READ_TIMEOUT_MS,
) {
    /** ¿Hay lo mínimo para hablar con el backend real? */
    val isBackendConfigured: Boolean
        get() = backendBaseUrl.isNotBlank() && apiKey.isNotBlank()
}

object NetworkDefaults {
    const val CONNECT_TIMEOUT_MS = 10_000L

    /** Debe superar el peor caso del backend: dos modelos de IA en serie + planificación. */
    const val READ_TIMEOUT_MS = 30_000L

    const val API_KEY_HEADER = "X-API-Key"
}
