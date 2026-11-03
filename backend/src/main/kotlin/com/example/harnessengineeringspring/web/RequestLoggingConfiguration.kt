package com.example.harnessengineeringspring.web

import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered

/**
 * Registers the request log filter for every `/api` path at the highest precedence, so it wraps the whole
 * chain before anything else could log a request target (design D6).
 */
@Configuration(proxyBeanMethods = false)
class RequestLoggingConfiguration {
    @Bean
    fun requestLoggingFilterRegistration(): FilterRegistrationBean<RequestLoggingFilter> =
        FilterRegistrationBean<RequestLoggingFilter>(RequestLoggingFilter()).apply {
            addUrlPatterns("/api/*")
            order = Ordered.HIGHEST_PRECEDENCE
        }
}
