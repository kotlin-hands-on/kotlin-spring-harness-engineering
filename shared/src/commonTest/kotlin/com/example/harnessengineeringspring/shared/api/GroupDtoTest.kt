package com.example.harnessengineeringspring.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Contract tests for the group DTOs (api-conventions: exact-JSON pins on JVM and Wasm). The pins encode
 * design D2/D3/D7: the token appears exactly once (creation), never on reads; `seq` is always emitted,
 * even at 0; absent request fields decode to their declared defaults.
 */
class GroupDtoTest {
    @Test
    fun `serializes a create-group request to the exact wire JSON`() {
        val encoded = ApiJson.json.encodeToString(CreateGroupRequest(name = "Trip", currency = "EUR"))

        assertEquals("""{"name":"Trip","currency":"EUR"}""", encoded)
    }

    @Test
    fun `round trips a create-group request`() {
        val decoded = ApiJson.json.decodeFromString<CreateGroupRequest>("""{"name": "Trip", "currency": "JPY"}""")

        assertEquals(CreateGroupRequest(name = "Trip", currency = "JPY"), decoded)
    }

    @Test
    fun `decodes an absent create-group field to its blank default`() {
        val onlyCurrency = ApiJson.json.decodeFromString<CreateGroupRequest>("""{"currency": "EUR"}""")
        val empty = ApiJson.json.decodeFromString<CreateGroupRequest>("""{}""")

        assertEquals(CreateGroupRequest(name = "", currency = "EUR"), onlyCurrency)
        assertEquals(CreateGroupRequest(name = "", currency = ""), empty)
    }

    @Test
    fun `pins the creation response JSON with the token present and seq emitted`() {
        val response = GroupResponse(
            id = "group-id",
            name = "Trip",
            currency = "EUR",
            fractionDigits = 2,
            token = "secret-token",
            seq = 0,
        )

        assertEquals(
            """{"id":"group-id","name":"Trip","currency":"EUR","fractionDigits":2,"token":"secret-token","seq":0}""",
            ApiJson.json.encodeToString(response),
        )
    }

    @Test
    fun `pins the read response JSON without the token but with seq`() {
        val response = GroupResponse(id = "group-id", name = "Trip", currency = "EUR", fractionDigits = 2, seq = 0)
        val encoded = ApiJson.json.encodeToString(response)

        assertEquals("""{"id":"group-id","name":"Trip","currency":"EUR","fractionDigits":2,"seq":0}""", encoded)
        assertFalse(encoded.contains("token"))
    }

    @Test
    fun `serializes an update-group request to the exact wire JSON`() {
        val encoded = ApiJson.json.encodeToString(UpdateGroupRequest(name = "New name"))

        assertEquals("""{"name":"New name"}""", encoded)
    }

    @Test
    fun `decodes an absent rename name to its blank default`() {
        val decoded = ApiJson.json.decodeFromString<UpdateGroupRequest>("""{}""")

        assertEquals(UpdateGroupRequest(name = ""), decoded)
    }

    @Test
    fun `round trips a read response and keeps a non-zero seq`() {
        val decoded = ApiJson.json.decodeFromString<GroupResponse>(
            """{"id":"group-id","name":"Trip","currency":"JPY","fractionDigits":0,"seq":3}""",
        )
        val expected = GroupResponse(id = "group-id", name = "Trip", currency = "JPY", fractionDigits = 0, seq = 3)

        assertEquals(expected, decoded)
    }
}
