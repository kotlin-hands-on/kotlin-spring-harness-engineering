package com.example.harnessengineeringspring.web

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.Arguments.arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals

class RequestLogRedactionTest {
    @ParameterizedTest
    @MethodSource("redactionCases")
    fun `redacts tokens wherever they appear in the target`(target: String, redacted: String) {
        assertEquals(redacted, redact(target))
    }

    companion object {
        @JvmStatic
        private fun redactionCases(): List<Arguments> = [
            arguments("/api/v1/groups/abc123/expenses", "/api/v1/groups/[REDACTED]/expenses"),
            arguments("/api/v1/groups/abc123", "/api/v1/groups/[REDACTED]"),
            arguments("/api/v1/groups/abc123/", "/api/v1/groups/[REDACTED]/"),
            arguments("/api/v1/expenses", "/api/v1/expenses"),
            arguments("/api/v1/groups/tok/a/groups/other", "/api/v1/groups/[REDACTED]/a/groups/other"),
            arguments("/api/v1/groups/", "/api/v1/groups/"),
            arguments("/api/v1/groups//expenses", "/api/v1/groups/[REDACTED]/expenses"),
            arguments("/api/v1/groups/tok%20x/y", "/api/v1/groups/[REDACTED]/y"),
            arguments("/api/v1/groups/g/x?token=secret", "/api/v1/groups/[REDACTED]/x?token=[REDACTED]"),
            arguments(
                "/api/v1/groups/g?accessToken=secret&filter=a",
                "/api/v1/groups/[REDACTED]?accessToken=[REDACTED]&filter=a",
            ),
            arguments("/api/v1/groups/g?token=", "/api/v1/groups/[REDACTED]?token=[REDACTED]"),
            arguments("/health?tokens=secret", "/health?tokens=secret"),
        ]
    }
}
