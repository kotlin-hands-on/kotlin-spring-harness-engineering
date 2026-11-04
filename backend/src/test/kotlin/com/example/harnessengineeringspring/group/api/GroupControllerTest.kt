package com.example.harnessengineeringspring.group.api

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.harnessengineeringspring.shared.api.ApiJson
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.support.IntegrationTest
import com.example.harnessengineeringspring.web.RequestLoggingFilter
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import java.security.MessageDigest
import java.util.HexFormat
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The group HTTP boundary through the shared integration context (design D2-D5): creating a group
 * returns 201 with its one-time secret token, rejects blank names and unresolvable currency codes as
 * `validation_failed`, issues distinct tokens, and persists only the tokens' SHA-256 digests.
 */
@IntegrationTest
class GroupControllerTest(@Autowired private val mockMvc: MockMvc, @Autowired private val jdbcTemplate: JdbcTemplate) {
    private val appender = ListAppender<ILoggingEvent>()
    private val requestLog = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger

    @BeforeEach
    fun attachAppender() {
        appender.start()
        requestLog.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        requestLog.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `creates a group with name, currency, fraction digits and a one-time token`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Trip", "currency": "EUR"}"""
        }.andExpect {
            status { isCreated() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { isNotEmpty() }
            jsonPath("$.name") { value("Trip") }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.fractionDigits") { value(2) }
            jsonPath("$.token") { isNotEmpty() }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `creates a group for a zero fraction digit currency`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Japan", "currency": "JPY"}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.currency") { value("JPY") }
            jsonPath("$.fractionDigits") { value(0) }
        }
    }

    @Test
    fun `rejects a blank group name without creating a group`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t ", "currency": "EUR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        assertEquals(0, countGroupsNamed(" \t "))
    }

    @Test
    fun `rejects a missing group name without creating a group`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"currency": "EUR"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
        }

        assertEquals(0, countGroupsNamed(""))
    }

    @Test
    fun `rejects an unresolvable currency code without creating a group`() {
        val name = "unresolvable-${UUID.randomUUID()}"

        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "currency": "NOPE"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        assertEquals(0, countGroupsNamed(name))
    }

    @Test
    fun `rejects a non-exact currency code without creating a group`() {
        val name = "non-exact-${UUID.randomUUID()}"

        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "currency": "E.R"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        assertEquals(0, countGroupsNamed(name))
    }

    @Test
    fun `rejects a create without a currency field without creating a group`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "X"}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }

        assertEquals(0, countGroupsNamed("X"))
    }

    @Test
    fun `reads a group by its token without echoing it`() {
        val created = createGroup(name = "Readable")
        val token = assertNotNull(created.token)

        mockMvc.get("/api/v1/groups/$token").andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.id") { value(created.id) }
            jsonPath("$.name") { value("Readable") }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.fractionDigits") { value(2) }
            jsonPath("$.token") { doesNotExist() }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `reads accept the atLeastSeq query parameter and report their seq`() {
        val created = createGroup(name = "Seq readable")
        val token = assertNotNull(created.token)

        mockMvc.get("/api/v1/groups/$token") {
            param("atLeastSeq", "42")
        }.andExpect {
            status { isOk() }
            jsonPath("$.seq") { value(0) }
        }
    }

    @Test
    fun `reads an unknown token as the not_found envelope`() {
        mockMvc.get("/api/v1/groups/unknown-token-value").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `reads a malformed token as the same not_found envelope as an unknown one`() {
        val unknown = notFoundBodyOf("/api/v1/groups/unknown-token-value")
        val malformed = notFoundBodyOf("/api/v1/groups/!!malformed!!")

        assertEquals(unknown, malformed)
    }

    @Test
    fun `logs a real group read with the token path segment redacted and never the token value`() {
        val created = createGroup(name = "Logged")
        val token = assertNotNull(created.token)

        mockMvc.get("/api/v1/groups/$token").andExpect { status { isOk() } }

        val lines = appender.list.map(ILoggingEvent::getFormattedMessage)
        assertEquals("GET /api/v1/groups/[REDACTED] 200", lines.last())
        assertTrue(lines.none { it.contains(token) }, "the token value leaked into a request log line")
    }

    @Test
    fun `renames a group keeping its id and currency`() {
        val created = createGroup(name = "Old name")
        val token = assertNotNull(created.token)

        mockMvc.patch("/api/v1/groups/$token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "New name"}"""
        }.andExpect {
            status { isOk() }
            jsonPath("$.id") { value(created.id) }
            jsonPath("$.name") { value("New name") }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.fractionDigits") { value(2) }
            jsonPath("$.token") { doesNotExist() }
            jsonPath("$.seq") { value(0) }
        }

        mockMvc.get("/api/v1/groups/$token").andExpect {
            status { isOk() }
            jsonPath("$.id") { value(created.id) }
            jsonPath("$.name") { value("New name") }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.fractionDigits") { value(2) }
        }
    }

    @Test
    fun `ignores a currency field in a rename request`() {
        val created = createGroup(name = "Currency fixed")
        val token = assertNotNull(created.token)

        mockMvc.patch("/api/v1/groups/$token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Renamed anyway", "currency": "USD"}"""
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/v1/groups/$token").andExpect {
            status { isOk() }
            jsonPath("$.currency") { value("EUR") }
            jsonPath("$.fractionDigits") { value(2) }
        }
    }

    @Test
    fun `rejects a blank rename and leaves the name unchanged`() {
        val created = createGroup(name = "Keep me")
        val token = assertNotNull(created.token)

        mockMvc.patch("/api/v1/groups/$token") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t "}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
        }

        mockMvc.get("/api/v1/groups/$token").andExpect {
            status { isOk() }
            jsonPath("$.name") { value("Keep me") }
        }
    }

    @Test
    fun `renames only a known token`() {
        mockMvc.patch("/api/v1/groups/unknown-token-value") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "New name"}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `renames only a known token even when the body is invalid`() {
        mockMvc.patch("/api/v1/groups/unknown-token-value") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t "}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `issues distinct tokens and stores only their sha-256 digests`() {
        val first = createGroup(name = "first-${UUID.randomUUID()}")
        val second = createGroup(name = "second-${UUID.randomUUID()}")
        val firstToken = assertNotNull(first.token)
        val secondToken = assertNotNull(second.token)

        assertNotEquals(firstToken, secondToken)

        val storedHashes = jdbcTemplate.queryForList(
            "select token_hash from groups where name in (?, ?)",
            String::class.java,
            first.name,
            second.name,
        )
        assertEquals(2, storedHashes.size)
        val expectedHashes: Set<String> = [sha256Hex(firstToken), sha256Hex(secondToken)]
        assertEquals(expectedHashes, storedHashes.toSet())
    }

    /** Asserts the route renders the `not_found` envelope and returns the full body for equality pins. */
    private fun notFoundBodyOf(url: String): String = mockMvc.get(url).andExpect {
        status { isNotFound() }
        jsonPath("$.error.type") { value("not_found") }
    }.andReturn().response.contentAsString

    private fun createGroup(name: String, currency: String = "EUR"): GroupResponse {
        val body = mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "$name", "currency": "$currency"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return ApiJson.json.decodeFromString<GroupResponse>(body)
    }

    private fun countGroupsNamed(name: String): Int? = jdbcTemplate.queryForObject(
        "select count(*) from groups where name = ?",
        Int::class.java,
        name,
    )

    /** Computes the digest independently of the production code, so the stored value is pinned, not echoed. */
    private fun sha256Hex(value: String): String =
        HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))
}
