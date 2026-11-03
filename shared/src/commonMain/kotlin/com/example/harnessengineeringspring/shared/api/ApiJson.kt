package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

/**
 * The single source of truth for the JSON configuration of the API wire contract.
 *
 * [contract] is applied both here (the shared contract tests run against [json]) and by the backend to its
 * HTTP `Json`, so the wire format the tests pin and the wire format the server speaks cannot diverge.
 *
 * Contract rationale:
 * - `ignoreUnknownKeys = true`: request fields the server does not know are accepted, so v1 can grow additively.
 * - `encodeDefaults = false` and `explicitNulls = false`: absence means "at its declared default"; optional
 *   fields can be added later without breaking the pinned JSON.
 * - the discriminator stays at the kotlinx default `"type"`.
 * - `isLenient = false` and `coerceInputValues = false`: structure violations are validation failures, never
 *   silently repaired.
 */
object ApiJson {
    /** The contract settings, applicable to any [JsonBuilder] via `builder.apply(ApiJson.contract)`. */
    val contract: JsonBuilder.() -> Unit = {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
        isLenient = false
        coerceInputValues = false
        classDiscriminator = "type"
    }

    /** A ready [Json] built from [contract]; the instance the shared contract tests run against. */
    val json: Json = Json(from = Json.Default, builderAction = contract)
}
