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
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Fuente de la ubicación actual. Devuelve null si no hay permiso, GPS o respuesta a tiempo. */
fun interface LocationSource {
    suspend fun currentLocation(): GeoPoint?
}

object LocationDefaults {
    /**
     * Tiempo máximo esperando una ubicación nueva. El GPS del reloj puede tardar
     * 20–30 s en el primer punto (sobre todo bajo techo y sin el teléfono cerca).
     */
    const val TIMEOUT_MS = 30_000L

    /** Una ubicación guardada más reciente que esto se usa sin esperar al GPS. */
    const val FRESH_FIX_MAX_AGE_MS = 2 * 60_000L

    /** Si no llega una ubicación nueva, se acepta una guardada hasta de esta edad. */
    const val FALLBACK_FIX_MAX_AGE_MS = 60 * 60_000L

    /** Cada cuánto se piden puntos mientras se espera el primero. */
    const val UPDATE_INTERVAL_MS = 1_000L

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
 * 2. Si no, se piden a la vez el GPS del reloj (alta precisión) y la ubicación
 *    por red o del teléfono emparejado (más rápida); gana la primera que llegue.
 * 3. Si nada responde en 30 s, se usa la última conocida (< 1 h).
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

    /** Primer punto que llegue del GPS o de la red/teléfono, o null si nada llega a tiempo. */
    private suspend fun freshLocation(): GeoPoint? = withTimeoutOrNull(timeoutMs) {
        merge(
            updates(Priority.PRIORITY_HIGH_ACCURACY),
            updates(Priority.PRIORITY_BALANCED_POWER_ACCURACY),
        ).first()
    }

    /** Puntos de ubicación con la prioridad dada, mientras alguien los escuche. */
    @SuppressLint("MissingPermission")
    private fun updates(priority: Int): Flow<GeoPoint> = callbackFlow {
        val request = LocationRequest.Builder(priority, LocationDefaults.UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(0)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it.toGeoPoint()) }
            }
        }
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Location.ageMillis(): Long =
        (SystemClock.elapsedRealtimeNanos() - elapsedRealtimeNanos) / 1_000_000L

    private fun Location.toGeoPoint() = GeoPoint(latitude, longitude)
}
