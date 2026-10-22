package com.busalert.wear.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.busalert.shared.domain.GeoPoint
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Fuente de la ubicación actual. Devuelve null si no hay permiso, GPS o respuesta a tiempo. */
fun interface LocationSource {
    suspend fun currentLocation(): GeoPoint?
}

object LocationDefaults {
    /** Tiempo máximo esperando una ubicación antes de pedirla por voz/teclado. */
    const val TIMEOUT_MS = 8_000L

    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

fun Context.hasLocationPermission(): Boolean = LocationDefaults.PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/** Implementación con FusedLocationProvider de Google Play Services. */
class FusedLocationSource(
    private val context: Context,
    private val timeoutMs: Long = LocationDefaults.TIMEOUT_MS,
) : LocationSource {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // El permiso se verifica justo antes.
    override suspend fun currentLocation(): GeoPoint? {
        if (!context.hasLocationPermission()) return null
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                    .await()
                    ?.let { GeoPoint(it.latitude, it.longitude) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalStateException) {
            // Play Services no disponible en el dispositivo.
            null
        } finally {
            cancellation.cancel()
        }
    }
}
