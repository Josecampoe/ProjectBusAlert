package com.busalert.shared.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ErrorCodeTest {
    @Test
    fun `mapea todos los códigos del backend`() {
        val expected = mapOf(
            "UNAUTHORIZED" to ErrorCode.UNAUTHORIZED,
            "RATE_LIMITED" to ErrorCode.RATE_LIMITED,
            "VALIDATION_ERROR" to ErrorCode.INVALID_REQUEST,
            "INVALID_JSON" to ErrorCode.INVALID_REQUEST,
            "PAYLOAD_TOO_LARGE" to ErrorCode.INVALID_REQUEST,
            "NOT_FOUND" to ErrorCode.NOT_FOUND,
            "ROUTE_NOT_FOUND" to ErrorCode.NOT_FOUND,
            "STOP_NOT_FOUND" to ErrorCode.NOT_FOUND,
            "AI_UNAVAILABLE" to ErrorCode.AI_UNAVAILABLE,
            "INTERNAL_ERROR" to ErrorCode.SERVER,
            "ALGO_NUEVO" to ErrorCode.UNKNOWN,
            null to ErrorCode.UNKNOWN,
        )
        expected.forEach { (wire, code) -> assertEquals(wire, code, ErrorCode.fromBackend(wire)) }
    }
}
