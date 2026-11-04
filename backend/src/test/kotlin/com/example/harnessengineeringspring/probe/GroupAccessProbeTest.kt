package com.example.harnessengineeringspring.probe

import com.example.harnessengineeringspring.shared.api.ApiJson
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import kotlin.test.Test

/**
 * Exercises the [com.example.harnessengineeringspring.group.GroupAccess] gate through the test-only
 * probe (design D5) - the stand-in for every future amount-carrying endpoint, which will call the same
 * gate. The token travels in the request body, never in a path or query, so the request log cannot
 * capture it.
 */
@IntegrationTest
class GroupAccessProbeTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `accepts an amount in the group's currency`() {
        val group = createGroup(currency = "EUR")

        mockMvc.post("/api/__probe/group-access/amount") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "${group.token}", "amount": {"minor": 1234, "currency": "EUR"}}"""
        }.andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.accepted") { value(true) }
            jsonPath("$.currency") { value("EUR") }
        }
    }

    @Test
    fun `rejects an amount in a different currency as currency_mismatch`() {
        val group = createGroup(currency = "EUR")

        mockMvc.post("/api/__probe/group-access/amount") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "${group.token}", "amount": {"minor": 1234, "currency": "USD"}}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.error.type") { value("currency_mismatch") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `rejects an amount with an unresolvable currency as validation_failed`() {
        val group = createGroup(currency = "EUR")

        mockMvc.post("/api/__probe/group-access/amount") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "${group.token}", "amount": {"minor": 1234, "currency": "NOPE"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `rejects an amount with a non-exact currency code as validation_failed`() {
        val group = createGroup(currency = "EUR")

        mockMvc.post("/api/__probe/group-access/amount") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "${group.token}", "amount": {"minor": 1, "currency": "E.R"}}"""
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `rejects an unknown token as not_found`() {
        mockMvc.post("/api/__probe/group-access/amount") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"token": "unknown-token-value", "amount": {"minor": 1234, "currency": "EUR"}}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    private fun createGroup(currency: String): GroupResponse {
        val body = mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Gate ${java.util.UUID.randomUUID()}", "currency": "$currency"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return ApiJson.json.decodeFromString<GroupResponse>(body)
    }
}
