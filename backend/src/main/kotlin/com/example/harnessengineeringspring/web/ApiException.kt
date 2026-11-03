package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.shared.api.ApiError

/**
 * Carries a domain rejection ([ApiError]) across the HTTP boundary to the one place that renders it
 * (see [ApiErrorAdvice]). A plain [RuntimeException] on purpose: exceptions are used only at this adapter
 * boundary, never for domain control flow - domain code returns sealed results.
 */
class ApiException(val error: ApiError) : RuntimeException(error.message)
