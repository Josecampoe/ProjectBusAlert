package com.busalert.shared.sync

/**
 * Contrato de sincronización reloj ↔ teléfono por la Data Layer API.
 * Ambas apps deben tener el mismo applicationId y firma para verse.
 */
object SyncContract {
    /** Ruta del DataItem con el estado del usuario. */
    const val PATH = "/busalert/sync"

    const val KEY_PAYLOAD = "payload"
    const val KEY_SCHEMA_VERSION = "schema_version"

    /** Versión del formato del payload; si no coincide, se ignora el mensaje. */
    const val SCHEMA_VERSION = 1
}
