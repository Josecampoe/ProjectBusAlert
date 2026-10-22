package com.busalert.shared.data

import com.busalert.shared.data.api.ErrorBodyDto
import com.busalert.shared.data.api.MalformedResponseException
import com.busalert.shared.domain.ErrorCode
import com.busalert.shared.domain.Outcome
import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.stream.MalformedJsonException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import java.io.InterruptedIOException

private val errorGson = Gson()

/**
 * Ejecuta una llamada al backend y convierte cualquier falla en un
 * [ErrorCode] estable. Nunca lanza (salvo cancelación de la corrutina).
 */
internal suspend fun <T> safeApiCall(block: suspend () -> T): Outcome<T> = try {
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
    e.response()?.errorBody()?.charStream()?.use { errorGson.fromJson(it, ErrorBodyDto::class.java) }?.error?.code
} catch (_: JsonParseException) {
    null
} catch (_: IOException) {
    null
}
