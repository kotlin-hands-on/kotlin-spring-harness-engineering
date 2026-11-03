package com.example.harnessengineeringspring.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ApiErrorTest {
    private data class Pin(val error: ApiError, val json: String)

    private val pins = [
        Pin(
            ApiError.ValidationFailed(message = "invalid input"),
            """{"type":"validation_failed","message":"invalid input"}""",
        ),
        Pin(
            ApiError.NotFound(message = "no such group"),
            """{"type":"not_found","message":"no such group"}""",
        ),
        Pin(
            ApiError.Conflict(message = "stale revision"),
            """{"type":"conflict","message":"stale revision"}""",
        ),
        Pin(
            ApiError.CurrencyMismatch(message = "group is EUR"),
            """{"type":"currency_mismatch","message":"group is EUR"}""",
        ),
    ]

    @Test
    fun `serializes each subtype with its pinned discriminator JSON`() {
        pins.forEach { (error, json) ->
            assertEquals(json, ApiJson.json.encodeToString(error))
        }
    }

    @Test
    fun `round trips every subtype through its discriminator`() {
        pins.forEach { (error, json) ->
            assertEquals(error, ApiJson.json.decodeFromString<ApiError>(json))
        }
    }

    @Test
    fun `wraps an error in the envelope shape`() {
        val encoded = ApiJson.json.encodeToString(
            ErrorResponse(error = ApiError.NotFound(message = "no such group")),
        )

        assertEquals("""{"error":{"type":"not_found","message":"no such group"}}""", encoded)
    }

    @Test
    fun `round trips the envelope`() {
        val decoded = ApiJson.json.decodeFromString<ErrorResponse>(
            """{"error":{"type":"currency_mismatch","message":"group is EUR"}}""",
        )

        assertEquals(ErrorResponse(error = ApiError.CurrencyMismatch(message = "group is EUR")), decoded)
    }
}
