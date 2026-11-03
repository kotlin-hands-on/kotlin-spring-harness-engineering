package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import kotlin.test.Test

private const val MARKER = "spa-index-marker"

@IntegrationTest
class SpaFallbackTest(@Autowired private val mockMvc: MockMvc) {
    @Test
    fun `serves the index page at the root`() {
        // MockMvc does not follow forwards, so the forward target of "/" is requested explicitly.
        val forwardedUrl = mockMvc.get("/").andReturn().response.forwardedUrl

        mockMvc.get("/" + requireNotNull(forwardedUrl) { "GET / must forward to the index page" }).andExpect {
            status { isOk() }
            content { contentTypeCompatibleWith(MediaType.TEXT_HTML) }
            content { string(org.hamcrest.Matchers.containsString(MARKER)) }
        }
    }

    @Test
    fun `falls back to the index page for client routes without a file extension`() {
        mockMvc.get("/some/client/route").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString(MARKER)) }
        }
    }

    @Test
    fun `serves wasm files as application wasm`() {
        mockMvc.get("/probe.wasm").andExpect {
            status { isOk() }
            content { contentType("application/wasm") }
        }
    }

    @Test
    fun `answers 404 for missing files with an extension`() {
        mockMvc.get("/missing.js").andExpect { status { isNotFound() } }
    }

    @Test
    fun `answers 404 without the index page for unknown api paths`() {
        mockMvc.get("/api/does-not-exist").andExpect {
            status { isNotFound() }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(MARKER))) }
        }
    }

    @Test
    fun `answers 404 without the index page for the bare api path`() {
        mockMvc.get("/api").andExpect {
            status { isNotFound() }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(MARKER))) }
        }
    }

    @Test
    fun `still falls back to the index page for paths that only look like api`() {
        mockMvc.get("/apix/route").andExpect {
            status { isOk() }
            content { string(org.hamcrest.Matchers.containsString(MARKER)) }
        }
    }

    @Test
    fun `answers 404 without the index page for unknown actuator paths`() {
        mockMvc.get("/actuator/does-not-exist").andExpect {
            status { isNotFound() }
            content { string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(MARKER))) }
        }
    }
}
