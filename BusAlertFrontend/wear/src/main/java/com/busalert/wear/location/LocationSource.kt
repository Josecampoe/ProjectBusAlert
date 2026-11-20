package com.busalert.wear.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
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
    /**
     * Tiempo máximo esperando el GPS del reloj. El GPS del reloj tarda más que
     * el del teléfono en el primer punto, sobre todo sin el teléfono cerca.
     */
    const val TIMEOUT_MS = 15_000L

    /** Una ubicación guardada más reciente que esto se usa sin esperar al GPS. */
    const val FRESH_FIX_MAX_AGE_MS = 2 * 60_000L

    /** Si el GPS no responde, se acepta una ubicación guardada hasta de esta edad. */
    const val FALLBACK_FIX_MAX_AGE_MS = 30 * 60_000L

    val PERMISSIONS = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )
}

fun Context.hasLocationPermission(): Boolean = LocationDefaults.PERMISSIONS.any {
    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
}

/** true si la ubicación del sistema está activada (Ajustes → Ubicación). Sin ella no hay GPS. */
fun Context.isLocationEnabled(): Boolean =
    getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.isLocationEnabled(it) } ?: false

/**
 * Implementación con FusedLocationProvider de Google Play Services:
 * 1. Si hay una ubicación reciente (< 2 min), se usa de una vez.
 * 2. Si no, se pide una nueva con el GPS del reloj (alta precisión).
 * 3. Si el GPS no responde a tiempo, se usa la última conocida (< 30 min).
 */
class FusedLocationSource(
    private val context: Context,
    private val timeoutMs: Long = LocationDefaults.TIMEOUT_MS,
) : LocationSource {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission") // El permiso se verifica justo antes.
    override suspend fun currentLocation(): GeoPoint? {
        if (!context.hasLocationPermission()) return null
        return try {
            val last = client.lastLocation.await()
            if (last != null && last.ageMillis() <= LocationDefaults.FRESH_FIX_MAX_AGE_MS) return last.toGeoPoint()
            freshLocation()
                ?: last?.takeIf { it.ageMillis() <= LocationDefaults.FALLBACK_FIX_MAX_AGE_MS }?.toGeoPoint()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            null
        } catch (e: IllegalStateException) {
            // Play Services no disponible en el dispositivo.
            null
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun freshLocation(): GeoPoint? {
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(timeoutMs) {
                client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                    .await()
                    ?.toGeoPoint()
            }
        } finally {
            cancellation.cancel()
        }
    }

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
