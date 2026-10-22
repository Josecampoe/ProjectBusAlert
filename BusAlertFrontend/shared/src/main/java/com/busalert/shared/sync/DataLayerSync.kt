package com.busalert.shared.sync

import android.content.Context
import android.util.Log
import com.busalert.shared.preferences.PreferencesRepository
import com.busalert.shared.preferences.SyncSnapshot
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await

private const val LOG_TAG = "BusAlertSync"

/** Publica el estado como DataItem; la Data Layer lo entrega al otro dispositivo. */
class DataLayerSyncPublisher(context: Context) : SyncPublisher {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    override suspend fun publish(snapshot: SyncSnapshot) {
        val request = PutDataMapRequest.create(SyncContract.PATH).apply {
            dataMap.putInt(SyncContract.KEY_SCHEMA_VERSION, SyncContract.SCHEMA_VERSION)
            dataMap.putString(SyncContract.KEY_PAYLOAD, SyncCodec.encodeSnapshot(snapshot))
        }.asPutDataRequest().setUrgent()
        try {
            dataClient.putDataItem(request).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            // Sin Play Services o sin dispositivo emparejado: la app sigue funcionando sin sincronizar.
            Log.w(LOG_TAG, "No se pudo publicar la sincronización: ${e.statusCode}")
        }
    }
}

/** Convierte un DataMap recibido en un estado aplicable, o null si no corresponde. */
fun decodeSyncPayload(schemaVersion: Int, payload: String?): SyncSnapshot? {
    if (schemaVersion != SyncContract.SCHEMA_VERSION || payload == null) return null
    return SyncCodec.decodeSnapshot(payload)
}

/**
 * Receptor de la Data Layer. Cada app lo extiende para indicar su repositorio
 * (sin el decorador que publica) y lo registra en su manifiesto.
 */
abstract class SyncListenerService : WearableListenerService() {

    protected abstract val localPreferences: PreferencesRepository

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val snapshots = dataEvents
            .filter { it.type == DataEvent.TYPE_CHANGED && it.dataItem.uri.path == SyncContract.PATH }
            .mapNotNull {
                val map = DataMapItem.fromDataItem(it.dataItem).dataMap
                decodeSyncPayload(map.getInt(SyncContract.KEY_SCHEMA_VERSION), map.getString(SyncContract.KEY_PAYLOAD))
            }
        dataEvents.release()
        // El servicio corre en un hilo de fondo: se aplica antes de devolver el control.
        runBlocking { snapshots.forEach { localPreferences.applySnapshot(it) } }
    }
}
