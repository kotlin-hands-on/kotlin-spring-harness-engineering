package com.example.harnessengineeringspring.web

import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

private const val STATIC_LOCATION = "classpath:/static/"
private const val INDEX = "index.html"

private val NON_SPA_ROOTS: Set<String> = ["api", "actuator"]

/**
 * Serves the bundled single page application from `classpath:/static/`.
 *
 * Paths that match no resource fall back to `index.html` so client side routes survive a reload. The fallback does
 * not apply to paths with a file extension (a missing `.js` is a real 404) nor to `/api` and `/actuator` (the bare
 * paths and everything beneath them).
 * The resource chain is deliberately uncached: a caching resolver would keep one entry per requested path, so
 * arbitrary client route paths falling back to `index.html` would grow the cache without bound.
 * The root path is forwarded to `index.html` by Boot's welcome page handling.
 */
@Configuration(proxyBeanMethods = false)
class SpaWebConfiguration : WebMvcConfigurer {
    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/**")
            .addResourceLocations(STATIC_LOCATION)
            .resourceChain(false)
            .addResolver(SpaFallbackResolver())
    }
}

private class SpaFallbackResolver : PathResourceResolver() {
    override fun getResource(resourcePath: String, location: Resource): Resource? {
        val requested = super.getResource(resourcePath, location)
        return when {
            requested?.isReadable == true -> requested
            isClientRoute(resourcePath) -> location.createRelative(INDEX).takeIf { it.isReadable }
            else -> null
        }
    }

    private fun isClientRoute(resourcePath: String): Boolean {
        val path = resourcePath.trimStart('/')
        val lastSegment = path.substringAfterLast('/')
        return path.substringBefore('/') !in NON_SPA_ROOTS && '.' !in lastSegment
    }
}
