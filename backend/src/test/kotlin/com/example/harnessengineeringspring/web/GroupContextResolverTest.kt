package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.group.ResolvedGroup
import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.shared.api.ApiJson
import com.example.harnessengineeringspring.shared.api.GroupResponse
import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

/**
 * The group-context resolver (vertical-slices ADR, decision D-4) through the shared integration context:
 * the direct component boundary - `resolveArgument` on a request carrying the `token` URI template
 * variable - and the HTTP-boundary wiring scenarios that must keep holding once the resolver is live.
 * The envelope rendering itself is pinned by `ApiErrorEnvelopeTest`.
 */
@IntegrationTest
class GroupContextResolverTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val resolver: GroupContextResolver,
) {
    @Test
    fun `resolves a created group's token to a verified group context`() {
        val created = createGroup()

        val resolved = assertIs<ResolvedGroup>(resolveContext(requestCarrying(assertNotNull(created.token))))

        assertEquals(created.id, resolved.group.id.value.toString())
    }

    @Test
    fun `rejects an unknown, blank or missing token as the same not_found error`() {
        val unknown = rejectedErrorOf(token = "unknown-token-value")
        val blank = rejectedErrorOf(token = "  ")
        val missing = rejectedErrorOf(token = null)

        assertEquals(unknown.message, blank.message)
        assertEquals(unknown.message, missing.message)
    }

    @Test
    fun `still answers an unknown token with the not_found envelope on the group read route`() {
        mockMvc.get("/api/v1/groups/unknown-token-value").andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `still answers an unknown token with the not_found envelope before body validation on the member add route`() {
        mockMvc.post("/api/v1/groups/unknown-token-value/members") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": " \t "}"""
        }.andExpect {
            status { isNotFound() }
            jsonPath("$.error.type") { value("not_found") }
        }
    }

    @Test
    fun `still creates a group on the token-less create route`() {
        mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Resolver ${UUID.randomUUID()}", "currency": "EUR"}"""
        }.andExpect {
            status { isCreated() }
        }
    }

    /** The error the resolver rejects [token] with; a missing [token] means no template variable at all. */
    private fun rejectedErrorOf(token: String?): ApiError.NotFound {
        val exception = assertFailsWith<ApiException> { resolveContext(requestCarrying(token)) }
        return assertIs<ApiError.NotFound>(exception.error)
    }

    private fun resolveContext(request: MockHttpServletRequest): Any? = resolver.resolveArgument(
        parameter = resolvedGroupParameter(),
        mavContainer = ModelAndViewContainer(),
        webRequest = ServletWebRequest(request),
        binderFactory = null,
    )

    /** A [MethodParameter] of type [ResolvedGroup] - the parameter shape the resolver applies to. */
    private fun resolvedGroupParameter(): MethodParameter =
        MethodParameter(javaClass.getDeclaredMethod("handler", ResolvedGroup::class.java), 0)

    @Suppress("UnusedPrivateMember") // reflectively referenced by [resolvedGroupParameter]
    private fun handler(@Suppress("UnusedParameter") context: ResolvedGroup) = Unit

    /** A request carrying the `token` URI template variable; a null [token] carries no variable at all. */
    private fun requestCarrying(token: String?): MockHttpServletRequest {
        val request = MockHttpServletRequest("GET", "/api/v1/groups/{token}")
        if (token != null) {
            request.setAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE, mapOf("token" to token))
        }
        return request
    }

    private fun createGroup(): GroupResponse {
        val body = mockMvc.post("/api/v1/groups") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"name": "Resolver ${UUID.randomUUID()}", "currency": "EUR"}"""
        }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
        return ApiJson.json.decodeFromString<GroupResponse>(body)
    }
}
