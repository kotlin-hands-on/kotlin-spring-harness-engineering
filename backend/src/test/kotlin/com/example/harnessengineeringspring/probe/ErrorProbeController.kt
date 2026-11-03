package com.example.harnessengineeringspring.probe

import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.web.ApiException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Test-only endpoints raising each error code, so the status map and envelope rendering of [ApiErrorAdvice]
 * are observable without any production route existing (design D8). The `/unhandled` endpoint throws an
 * exception no handler catches, so behaviour around failures that escape the dispatcher - the request log,
 * design D6 - is observable too.
 */
@RestController
@RequestMapping("/api/__probe/errors")
class ErrorProbeController {
    @GetMapping("/validation_failed")
    fun validationFailed(): Nothing = throw ApiException(ApiError.ValidationFailed(message = "probe: invalid input"))

    @GetMapping("/not_found")
    fun notFound(): Nothing = throw ApiException(ApiError.NotFound(message = "probe: no such group"))

    @GetMapping("/conflict")
    fun conflict(): Nothing = throw ApiException(ApiError.Conflict(message = "probe: stale revision"))

    @GetMapping("/currency_mismatch")
    fun currencyMismatch(): Nothing = throw ApiException(ApiError.CurrencyMismatch(message = "probe: group is EUR"))

    @GetMapping("/unhandled")
    fun unhandled(): Nothing = throw IllegalStateException("probe: unexpected failure")
}
