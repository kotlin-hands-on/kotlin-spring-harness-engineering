package com.example.harnessengineeringspring.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.NestedExceptionUtils
import org.springframework.web.filter.OncePerRequestFilter

/**
 * The backend's request log (design D6): exactly one INFO line per request under `/api`, carrying method,
 * the [redacted][redact] target and the resulting status. The target is the only token-carrying part
 * of the request and is always passed through [redact] before it reaches the log.
 *
 * The line is also emitted when the chain throws: a response that never completed has no resulting status
 * (the container sets 500 only later, during its own error handling), so the most specific cause of the
 * exception is logged in the status's place and the exception is rethrown untouched, so the container's
 * error handling still runs. Without this, exactly the anomalous requests - the ones the log exists for -
 * would be logged zero times.
 */
class RequestLoggingFilter : OncePerRequestFilter() {
    // The boundary must see everything crossing the filter: the broad catch logs and rethrows, it never
    // swallows (kotlin-engineer policy: Exception, not Throwable).
    @Suppress("TooGenericExceptionCaught")
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        try {
            filterChain.doFilter(request, response)
        } catch (exception: Exception) {
            val failure = NestedExceptionUtils.getMostSpecificCause(exception)
            log.info("{} {} {}", request.method, redact(targetOf(request)), "error: ${failure.javaClass.simpleName}")
            throw exception
        }
        log.info("{} {} {}", request.method, redact(targetOf(request)), response.status)
    }

    private fun targetOf(request: HttpServletRequest): String {
        val query = request.queryString ?: return request.requestURI
        return "${request.requestURI}?$query"
    }

    companion object {
        // Named `log`: the inherited protected Java field `logger` (Commons Logging) would clash (KT-56386).
        private val log = LoggerFactory.getLogger(RequestLoggingFilter::class.java)
    }
}
