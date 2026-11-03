package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test

@IntegrationTest
class ApiErrorEnvelopeTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `renders validation_failed as 400 in the error envelope`() {
        mockMvc.get("/api/__probe/errors/validation_failed").andExpect {
            status { isBadRequest() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error.type") { value("validation_failed") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `renders not_found as 404 in the error envelope`() {
        mockMvc.get("/api/__probe/errors/not_found").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error.type") { value("not_found") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `renders conflict as 409 in the error envelope`() {
        mockMvc.get("/api/__probe/errors/conflict").andExpect {
            status { isConflict() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error.type") { value("conflict") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `renders currency_mismatch as 422 in the error envelope`() {
        mockMvc.get("/api/__probe/errors/currency_mismatch").andExpect {
            status { isUnprocessableEntity() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error.type") { value("currency_mismatch") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }

    @Test
    fun `renders an unmatched api route as the 404 not_found envelope`() {
        mockMvc.get("/api/v1/does-not-exist").andExpect {
            status { isNotFound() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.error.type") { value("not_found") }
            jsonPath("$.error.message") { isNotEmpty() }
        }
    }
}
