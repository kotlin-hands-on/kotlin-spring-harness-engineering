package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.group.GroupService
import com.example.harnessengineeringspring.group.GroupToken
import com.example.harnessengineeringspring.group.ResolvedGroup
import com.example.harnessengineeringspring.shared.api.ApiError
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.stereotype.Component
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Resolves the `{token}` path segment to a verified [ResolvedGroup] (vertical-slices ADR, decision D-4):
 * applies only to handler methods that declare a [ResolvedGroup] parameter, so token-less routes
 * (e.g. `POST /api/v1/groups`) are unaffected - they simply declare no resolved parameter. A token
 * matching no group is rejected as `not_found` before any handler runs, so controllers never touch
 * raw tokens.
 */
@Component
class GroupContextResolver(private val groups: GroupService) : HandlerMethodArgumentResolver {

    /** True for exactly the [ResolvedGroup] parameter type - the resolver's only applicability. */
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.parameterType == ResolvedGroup::class.java

    /**
     * Reads the `token` URI template variable and resolves it via the group slice's root service. A
     * missing, blank or unknown token is rejected as the indistinguishable `not_found` envelope
     * (design D-C) - the same rejection the group read route reports.
     */
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): ResolvedGroup {
        val resolved = groups.resolve(GroupToken(tokenVariableOf(webRequest))) ?: noSuchGroup()
        return resolved
    }

    private fun tokenVariableOf(webRequest: NativeWebRequest): String {
        val variables = webRequest.getAttribute(
            HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST,
        ) as? Map<*, *>
        val raw = variables?.get("token") as? String
        if (raw == null || raw.isBlank()) noSuchGroup()
        return raw
    }

    private fun noSuchGroup(): Nothing = throw ApiException(ApiError.NotFound(message = "no such group"))
}

/**
 * Registers the [GroupContextResolver] with WebMvc (vertical-slices ADR, decision D-4): handler methods
 * declaring a [ResolvedGroup] parameter receive the verified group context; no other request handling
 * changes.
 */
@Configuration(proxyBeanMethods = false)
class GroupContextResolverConfiguration(private val resolver: GroupContextResolver) : WebMvcConfigurer {
    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.add(resolver)
    }
}
