package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ApiJsonTest {
    @Serializable
    private data class Sample(val name: String, val count: Int = 0, val note: String?)

    @Serializable
    private sealed interface Kind {
        @Serializable
        @SerialName("first")
        data class One(val value: String) : Kind

        @Serializable
        @SerialName("second")
        data class Two(val value: Int) : Kind
    }

    @Test
    fun `ignores unknown request fields when decoding`() {
        val decoded = ApiJson.json.decodeFromString<Sample>("""{"name": "x", "unknownField": 42}""")

        assertEquals(Sample(name = "x", count = 0, note = null), decoded)
    }

    @Test
    fun `omits response fields at their default value when encoding`() {
        val atDefault = ApiJson.json.encodeToString(Sample(name = "x", count = 0, note = "n"))
        val offDefault = ApiJson.json.encodeToString(Sample(name = "x", count = 7, note = "n"))

        assertFalse(atDefault.contains("\"count\""))
        assertContains(offDefault, "\"count\":7")
    }

    @Test
    fun `omits explicit nulls when encoding and accepts absence when decoding`() {
        val encoded = ApiJson.json.encodeToString(Sample(name = "x", count = 0, note = null))
        val decoded = ApiJson.json.decodeFromString<Sample>("""{"name": "x"}""")

        assertFalse(encoded.contains("\"note\""))
        assertEquals(Sample(name = "x", count = 0, note = null), decoded)
    }

    @Test
    fun `emits the type discriminator with the serial name of the sealed subtype`() {
        val encoded = ApiJson.json.encodeToString(Kind.One("x") as Kind)
        val decoded = ApiJson.json.decodeFromString<Kind>("""{"type": "second", "value": 2}""")

        assertContains(encoded, "\"type\":\"first\"")
        assertEquals(Kind.Two(2), decoded)
    }

    @Test
    fun `rejects non JSON structure instead of repairing it`() {
        assertFailsWith<SerializationException> {
            ApiJson.json.decodeFromString<Sample>("""{name: "x", note: null}""")
        }
    }

    @Test
    fun `rejects an explicit null for a non-nullable defaulted field instead of coercing it to the default`() {
        assertFailsWith<SerializationException> {
            ApiJson.json.decodeFromString<Sample>("""{"name": "x", "count": null, "note": "n"}""")
        }
    }
}
