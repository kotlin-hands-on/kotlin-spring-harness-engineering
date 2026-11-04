package com.example.harnessengineeringspring.member.api

import com.example.harnessengineeringspring.shared.api.ApiJson
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.shared.api.MemberListResponse
import com.example.harnessengineeringspring.shared.api.MemberResponse
import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * The member HTTP boundary through the shared integration context (design D6): adding and listing
 * members is scoped to the group addressed by the token, names are unique-free within a group, the
 * list is deterministically ordered by name, and every payload carries the member's active state and
 * the group's `seq`.
 */
@IntegrationTest
class MemberControllerTest(@Autowired private val mockMvc: MockMvc, @Autowired private val jdbcTemplate: JdbcTemplate) {
    @Test
    fun `adds a member to the addressed group`() {
        val group = createGroup()

        mockMvc.post("/api/v1/groups/${group.token}/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alice"}"""
        }.andExpect {
            status { isCreated() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { isNotEmpty() }
            jsonPath("$.name") { value("Alice") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.seq") { value(0) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[0].active") { value(true) }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `allows duplicate member names within a group`() {
        val group = createGroup()
        addMember(group, name = "Alice")
        addMember(group, name = "Alice")

        val body = mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(2) }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[1].name") { value("Alice") }
        }.andReturn().response.contentAsString

        val ids = ApiJson.json.decodeFromString<MemberListResponse>(body).members.map { it.id }
        assertEquals(ids.sorted(), ids, "equal names must be ordered deterministically by their id")
    }

    @Test
    fun `rejects a blank member name without creating a member`() {
        val group = createGroup()

        mockMvc.post("/api/v1/groups/${group.token}/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(0) }
        }
    }

    @Test
    fun `rejects a missing member name without creating a member`() {
        val group = createGroup()

        mockMvc.post("/api/v1/groups/${group.token}/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(0) }
        }
    }

    @Test
    fun `adds members only to a known group token`() {
        mockMvc.post("/api/v1/groups/unknown-token-value/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alice"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `lists members of the addressed group ordered by name`() {
        val group = createGroup()
        addMember(group, name = "Bob")
        addMember(group, name = "Carol")
        addMember(group, name = "Alice")

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(3) }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[1].name") { value("Bob") }
            jsonPath("$.members[2].name") { value("Carol") }
            jsonPath("$.members[0].active") { value(true) }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `lists an empty group as an empty list`() {
        val group = createGroup()

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.members.length()") { value(0) }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `lists members only for a known group token`() {
        mockMvc.get("/api/v1/groups/unknown-token-value/members").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `keeps members of other groups out of the list`() {
        val home = createGroup()
        val other = createGroup()
        addMember(home, name = "Alice")
        addMember(other, name = "Bob")

        mockMvc.get("/api/v1/groups/${home.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].name") { value("Alice") }
        }
    }

    @Test
    fun `accepts the atLeastSeq parameter on the member list`() {
        val group = createGroup()

        mockMvc.get("/api/v1/groups/${group.token}/members") {
            param("atLeastSeq", "7")
        }.andExpect {
            status { isOk() }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `renames a member keeping its id and active state`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alicia"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(member.id) }
            jsonPath("$.name") { value("Alicia") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.seq") { value(0) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].id") { value(member.id) }
            jsonPath("$.members[0].name") { value("Alicia") }
            jsonPath("$.members[0].active") { value(true) }
        }
    }

    @Test
    fun `deactivates a member keeping it in the list`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"active": false}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(member.id) }
            jsonPath("$.active") { value(false) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[0].active") { value(false) }
        }
    }

    @Test
    fun `reactivates an inactive member`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")
        setActive(group, member, active = false)

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"active": true}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.active") { value(true) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members[0].active") { value(true) }
        }
    }

    @Test
    fun `leaves a member unchanged on an empty update`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(member.id) }
            jsonPath("$.name") { value("Alice") }
            jsonPath("$.active") { value(true) }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `rejects a blank name in a member update and leaves the member unchanged`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[0].active") { value(true) }
        }
    }

    @Test
    fun `updates no unknown member id`() {
        val group = createGroup()
        val unknown = UUID.randomUUID()

        mockMvc.patch("/api/v1/groups/${group.token}/members/$unknown") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alicia"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `treats a malformed member id as not found`() {
        val group = createGroup()

        mockMvc.patch("/api/v1/groups/${group.token}/members/not-a-uuid") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alicia"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `updates no member of another group`() {
        val home = createGroup()
        val other = createGroup()
        val member = addMember(other, name = "Bob")

        mockMvc.patch("/api/v1/groups/${home.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Bobby"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }

        mockMvc.get("/api/v1/groups/${other.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members[0].name") { value("Bob") }
        }
    }

    @Test
    fun `updates no member for an unknown group token`() {
        mockMvc.patch("/api/v1/groups/unknown-token-value/members/${UUID.randomUUID()}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Alicia"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `lists active and deactivated members with their state ordered by name`() {
        val group = createGroup()
        addMember(group, name = "Bob")
        addMember(group, name = "Carol")
        addMember(group, name = "Alice")
        val bob = memberNamed(group, name = "Bob")
        setActive(group, bob, active = false)

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(3) }
            jsonPath("$.members[0].name") { value("Alice") }
            jsonPath("$.members[0].active") { value(true) }
            jsonPath("$.members[1].name") { value("Bob") }
            jsonPath("$.members[1].active") { value(false) }
            jsonPath("$.members[2].name") { value("Carol") }
            jsonPath("$.members[2].active") { value(true) }
        }
    }

    @Test
    fun `deletes an unreferenced active member`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")

        mockMvc.delete("/api/v1/groups/${group.token}/members/${member.id}").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.seq") { value(0) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(0) }
        }

        mockMvc.patch("/api/v1/groups/${group.token}/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Zombie"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `deletes an unreferenced deactivated member`() {
        val group = createGroup()
        val member = addMember(group, name = "Alice")
        setActive(group, member, active = false)

        mockMvc.delete("/api/v1/groups/${group.token}/members/${member.id}").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.seq") { value(0) }
        }

        mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(0) }
        }
    }

    @Test
    fun `deletes no unknown member id`() {
        val group = createGroup()

        mockMvc.delete("/api/v1/groups/${group.token}/members/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `deletes no member of another group`() {
        val home = createGroup()
        val other = createGroup()
        val member = addMember(other, name = "Bob")

        mockMvc.delete("/api/v1/groups/${home.token}/members/${member.id}").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }

        mockMvc.get("/api/v1/groups/${other.token}/members").andExpect {
            status { isOk() }
            jsonPath("$.members.length()") { value(1) }
            jsonPath("$.members[0].name") { value("Bob") }
        }
    }

    @Test
    fun `treats a malformed member id as not found on delete`() {
        val group = createGroup()

        mockMvc.delete("/api/v1/groups/${group.token}/members/not-a-uuid").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `deletes no member for an unknown group token`() {
        mockMvc.delete("/api/v1/groups/unknown-token-value/members/${UUID.randomUUID()}").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `rejects deleting a member referenced by a ledger stand-in and allows it once unreferenced`() {
        val group = createGroup()
        val member = addMember(group, name = "Referenced")
        jdbcTemplate.update("drop table if exists $STAND_IN_TABLE")
        jdbcTemplate.update(
            """
            create table $STAND_IN_TABLE (
                member_id uuid not null references members(id) on delete restrict
            )
            """.trimIndent(),
        )
        try {
            jdbcTemplate.update("insert into $STAND_IN_TABLE (member_id) values (?::uuid)", member.id)

            mockMvc.delete("/api/v1/groups/${group.token}/members/${member.id}").andExpect {
                status { isConflict() }
                jsonPath("$.error.type") { value("conflict") }
                jsonPath("$.error.message") { isNotEmpty() }
            }

            mockMvc.get("/api/v1/groups/${group.token}/members").andExpect {
                status { isOk() }
                jsonPath("$.members.length()") { value(1) }
                jsonPath("$.members[0].name") { value("Referenced") }
            }

            jdbcTemplate.update("delete from $STAND_IN_TABLE")

            mockMvc.delete("/api/v1/groups/${group.token}/members/${member.id}").andExpect {
                status { isOk() }
                jsonPath("$.seq") { value(0) }
            }
        } finally {
            jdbcTemplate.update("drop table if exists $STAND_IN_TABLE")
        }
    }

    private fun setActive(group: GroupResponse, member: MemberResponse, active: Boolean) {
        val token = assertNotNull(group.token)
        mockMvc.patch("/api/v1/groups/$token/members/${member.id}") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"active": $active}"""
        }.andExpect { status { isOk() } }
    }

    private fun memberNamed(group: GroupResponse, name: String): MemberResponse {
        val token = assertNotNull(group.token)
        val body = mockMvc.get("/api/v1/groups/$token/members").andReturn().response.contentAsString
        val list = ApiJson.json.decodeFromString<MemberListResponse>(body)
        return list.members.first { it.name == name }
    }

    private companion object {
        /** Test-scoped stand-in for the future `ledger_event` FK (design D6); no `ledger-core` table overlaps. */
        const val STAND_IN_TABLE = "member_delete_guard_probe"
    }

    private fun createGroup(): GroupResponse {
        val body = mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Members ${java.util.UUID.randomUUID()}", "currency": "EUR"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return ApiJson.json.decodeFromString<GroupResponse>(body)
    }

    private fun addMember(group: GroupResponse, name: String): MemberResponse {
        val token = assertNotNull(group.token)
        val body = mockMvc.post("/api/v1/groups/$token/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return ApiJson.json.decodeFromString<MemberResponse>(body)
    }
}
