package com.example.harnessengineeringspring.shared.api

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Contract tests for the member DTOs (api-conventions: exact-JSON pins on JVM and Wasm). The pins
 * encode design D3/D7: `active` and `seq` are always emitted - an inactive member must not lose its
 * `active: false` - and the list carries its `seq` even when empty.
 */
class MemberDtoTest {
    @Test
    fun `serializes an add-member request to the exact wire JSON`() {
        val encoded = ApiJson.json.encodeToString(AddMemberRequest(name = "Alice"))

        assertEquals("""{"name":"Alice"}""", encoded)
    }

    @Test
    fun `decodes an absent member name to its blank default`() {
        val decoded = ApiJson.json.decodeFromString<AddMemberRequest>("""{}""")

        assertEquals(AddMemberRequest(name = ""), decoded)
    }

    @Test
    fun `pins an active member with active and seq emitted`() {
        val member = MemberResponse(id = "member-id", name = "Alice", active = true, seq = 0)

        assertEquals(
            """{"id":"member-id","name":"Alice","active":true,"seq":0}""",
            ApiJson.json.encodeToString(member),
        )
    }

    @Test
    fun `pins a deactivated member still emitting active false and seq`() {
        val member = MemberResponse(id = "member-id", name = "Bob", active = false, seq = 0)

        assertEquals(
            """{"id":"member-id","name":"Bob","active":false,"seq":0}""",
            ApiJson.json.encodeToString(member),
        )
    }

    @Test
    fun `round trips a member and keeps a non-zero seq`() {
        val decoded = ApiJson.json.decodeFromString<MemberResponse>(
            """{"id":"member-id","name":"Alice","active":true,"seq":3}""",
        )

        assertEquals(MemberResponse(id = "member-id", name = "Alice", active = true, seq = 3), decoded)
    }

    @Test
    fun `pins the populated member list JSON`() {
        val member = MemberResponse(id = "member-id", name = "Alice", active = true, seq = 0)
        val list = MemberListResponse(members = [member], seq = 0)

        assertEquals(
            """{"members":[{"id":"member-id","name":"Alice","active":true,"seq":0}],"seq":0}""",
            ApiJson.json.encodeToString(list),
        )
    }

    @Test
    fun `pins the empty member list JSON with its seq`() {
        val empty = MemberListResponse(members = [], seq = 0)

        assertEquals("""{"members":[],"seq":0}""", ApiJson.json.encodeToString(empty))
    }

    @Test
    fun `pins the delete-member response JSON with its seq`() {
        val response = DeleteMemberResponse(seq = 0)

        assertEquals("""{"seq":0}""", ApiJson.json.encodeToString(response))
    }

    @Test
    fun `round trips a delete-member response and keeps a non-zero seq`() {
        val decoded = ApiJson.json.decodeFromString<DeleteMemberResponse>("""{"seq":3}""")

        assertEquals(DeleteMemberResponse(seq = 3), decoded)
    }

    @Test
    fun `omits an absent name from an update-member request`() {
        val encoded = ApiJson.json.encodeToString(UpdateMemberRequest(name = "New name"))

        assertEquals("""{"name":"New name"}""", encoded)
    }

    @Test
    fun `omits an absent active flag from an update-member request`() {
        val encoded = ApiJson.json.encodeToString(UpdateMemberRequest(active = false))

        assertEquals("""{"active":false}""", encoded)
    }

    @Test
    fun `encodes an empty member update as an empty object`() {
        val encoded = ApiJson.json.encodeToString(UpdateMemberRequest())

        assertEquals("""{}""", encoded)
    }

    @Test
    fun `decodes an update-member request with absent fields as nulls`() {
        val onlyActive = ApiJson.json.decodeFromString<UpdateMemberRequest>("""{"active": false}""")
        val empty = ApiJson.json.decodeFromString<UpdateMemberRequest>("""{}""")

        assertEquals(UpdateMemberRequest(name = null, active = false), onlyActive)
        assertEquals(UpdateMemberRequest(name = null, active = null), empty)
    }
}
