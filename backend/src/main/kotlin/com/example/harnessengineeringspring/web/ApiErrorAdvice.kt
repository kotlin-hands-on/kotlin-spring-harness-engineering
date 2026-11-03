package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.shared.api.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.resource.NoResourceFoundException

/**
 * The single place that maps an [ApiError] to its HTTP status and renders the `{"error":{...}}` envelope:
 *
 * `validation_failed` -> 400, `not_found` -> 404, `conflict` -> 409, `currency_mismatch` -> 422.
 *
 * It also renders the container's no-handler exception as the `not_found` envelope for paths under `/api/`,
 * so an unknown API route gets the JSON envelope instead of the default 404 page; other paths keep the
 * framework's default handling (the rethrown exception is recognized as intentional, not logged as a failure).
 */
@RestControllerAdvice
class ApiErrorAdvice {
    @ExceptionHandler(ApiException::class)
    fun handleApiException(exception: ApiException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(statusOf(exception.error)).body(ErrorResponse(error = exception.error))

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleUnmatchedRoute(
        exception: NoResourceFoundException,
        request: HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        if (!request.isApiPath()) throw exception
        return notFoundEnvelope()
    }

    private fun statusOf(error: ApiError): HttpStatus = when (error) {
        is ApiError.ValidationFailed -> HttpStatus.BAD_REQUEST
        is ApiError.NotFound -> HttpStatus.NOT_FOUND
        is ApiError.Conflict -> HttpStatus.CONFLICT
        is ApiError.CurrencyMismatch -> HttpStatus.UNPROCESSABLE_CONTENT
    }

    private fun notFoundEnvelope(): ResponseEntity<ErrorResponse> = ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse(error = ApiError.NotFound(message = "no such route")))

    private fun HttpServletRequest.isApiPath(): Boolean = requestURI == "/api" || requestURI.startsWith("/api/")
}
