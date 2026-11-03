package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The closed set of domain rejections, rendered on the wire as `{"type":"<code>","message":"<text>"}`.
 *
 * The serial names are the stable contract; the message is human-oriented and never parsed. Subtype extras
 * (e.g. a future `expectedRevision` on [Conflict]) must be defaulted properties so `encodeDefaults = false`
 * keeps today's pinned JSON unchanged.
 */
@Serializable
sealed interface ApiError {
    val message: String

    /** Invalid input; HTTP 400. */
    @Serializable
    @SerialName("validation_failed")
    data class ValidationFailed(override val message: String) : ApiError

    /** The addressed resource does not exist; HTTP 404. */
    @Serializable
    @SerialName("not_found")
    data class NotFound(override val message: String) : ApiError

    /** The write conflicts with current state (e.g. a stale revision); HTTP 409. */
    @Serializable
    @SerialName("conflict")
    data class Conflict(override val message: String) : ApiError

    /** An amount's currency differs from the group's fixed currency; HTTP 422. */
    @Serializable
    @SerialName("currency_mismatch")
    data class CurrencyMismatch(override val message: String) : ApiError
}
