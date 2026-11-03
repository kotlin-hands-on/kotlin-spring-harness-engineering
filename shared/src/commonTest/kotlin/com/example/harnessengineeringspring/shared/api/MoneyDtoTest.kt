package com.example.harnessengineeringspring.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

class MoneyDtoTest {
    @Test
    fun `serializes 1234 EUR minors to the exact wire JSON`() {
        val encoded = ApiJson.json.encodeToString(MoneyDto(minor = 1234, currency = "EUR"))

        assertEquals("""{"minor":1234,"currency":"EUR"}""", encoded)
    }

    @Test
    fun `round trips through the exact wire JSON`() {
        val decoded = ApiJson.json.decodeFromString<MoneyDto>("""{"minor":1234,"currency":"EUR"}""")

        assertEquals(MoneyDto(minor = 1234, currency = "EUR"), decoded)
    }

    @Test
    fun `round trips a zero fraction currency without introducing digits`() {
        val original = MoneyDto(minor = 300, currency = "JPY")

        val decoded = ApiJson.json.decodeFromString<MoneyDto>(ApiJson.json.encodeToString(original))

        assertEquals(original, decoded)
    }
}
