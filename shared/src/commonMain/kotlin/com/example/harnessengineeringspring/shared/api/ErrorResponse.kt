package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * The envelope every domain rejection is returned in: `{"error":{"type":"<code>","message":"<text>"}}`.
 */
@Serializable
data class ErrorResponse(val error: ApiError)
