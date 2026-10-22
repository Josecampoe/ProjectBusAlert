package com.busalert.shared.data

import com.busalert.shared.data.api.BusAlertApi
import com.busalert.shared.data.api.ErrorBodyDto
import com.busalert.shared.data.api.MalformedResponseException
import com.busalert.shared.data.api.toDomain
import com.busalert.shared.data.api.toDto
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.busalert.shared.domain.TripAssistantRepository
import com.busalert.shared.domain.TripPlan
import com.busalert.shared.domain.TripQuery
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

/** Implementación real: llama a `POST /api/voice` del backend. */
class RemoteTripAssistantRepository(
    private val api: BusAlertApi,
    private val gson: Gson = Gson(),
) : TripAssistantRepository {

    override suspend fun planTrip(query: TripQuery): Outcome<TripPlan> = safeCall {
        api.planTrip(query.toDto()).toDomain()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): Outcome<T> = try {
        Outcome.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Outcome.Failure(ErrorCode.fromBackend(parseErrorCode(e)))
    } catch (e: MalformedJsonException) {
        // Hereda de IOException, pero es un problema del contrato, no de la red.
        Outcome.Failure(ErrorCode.SERVER)
    } catch (e: InterruptedIOException) {
        Outcome.Failure(ErrorCode.TIMEOUT)
    } catch (e: IOException) {
        Outcome.Failure(ErrorCode.NETWORK)
    } catch (e: MalformedResponseException) {
        Outcome.Failure(ErrorCode.SERVER)
    } catch (e: JsonParseException) {
        Outcome.Failure(ErrorCode.SERVER)
    }

    private fun parseErrorCode(e: HttpException): String? = try {
        e.response()?.errorBody()?.charStream()?.use { gson.fromJson(it, ErrorBodyDto::class.java) }?.error?.code
    } catch (_: JsonParseException) {
        null
    } catch (_: IOException) {
        null
    }
}
