package com.example.harnessengineeringspring.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.harnessengineeringspring.support.IntegrationTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/**
 * End-to-end pin of the request log (design D6): one INFO line per request under `/api`, carrying method,
 * redacted target and status, with the token value never present and the request still served normally.
 * The line is also emitted when an unhandled exception escapes the request - with the failure's class in
 * place of the status - and the exception still propagates to the caller.
 */
@IntegrationTest
class RequestLoggingTest(@Autowired private val mockMvc: MockMvc) {
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger

    @BeforeEach
    fun attachAppender() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detachAppender() {
        logger.detachAppender(appender)
        appender.stop()
    }

    @Test
    fun `logs one line per api request with the token path segment redacted`() {
        mockMvc.get("/api/v1/groups/secret-token/expenses").andExpect {
            status { isNotFound() }
        }

        assertEquals("GET /api/v1/groups/[REDACTED]/expenses 404", singleLoggedLine())
    }

    @Test
    fun `logs one line per api request with the token query parameter redacted`() {
        mockMvc.get("/api/__probe/json?token=secret-query-token").andExpect {
            status { isOk() }
        }

        assertEquals("GET /api/__probe/json?token=[REDACTED] 200", singleLoggedLine())
    }

    @Test
    fun `logs one line even when an unhandled exception escapes the request`() {
        val failure = assertFailsWith<Exception> { mockMvc.get("/api/__probe/errors/unhandled") }

        val rootCause = generateSequence<Throwable>(failure) { it.cause }.last()
        assertIs<IllegalStateException>(rootCause)
        assertEquals("probe: unexpected failure", rootCause.message)

        assertEquals("GET /api/__probe/errors/unhandled error: IllegalStateException", singleLoggedLine())
    }

    @Test
    fun `logs the error line with the token query parameter redacted`() {
        assertFailsWith<Exception> { mockMvc.get("/api/__probe/errors/unhandled?token=secret") }

        assertEquals(
            "GET /api/__probe/errors/unhandled?token=[REDACTED] error: IllegalStateException",
            singleLoggedLine(),
        )
    }

    @Test
    fun `does not log requests outside the api`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
        }

        assertEquals(emptyList<String>(), appender.list.map(ILoggingEvent::getFormattedMessage))
    }

    private fun singleLoggedLine(): String = appender.list.map(ILoggingEvent::getFormattedMessage).single()
}
