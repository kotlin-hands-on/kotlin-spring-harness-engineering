package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * Request to rename a group (design D3): only `name` is accepted. The group's currency is fixed at
 * creation - a `currency` field in the body is an unknown field and is ignored per the tolerant
 * decoding convention.
 *
 * `name` defaults to the empty string so an *absent* name decodes to the domain-level blank rejection
 * (`validation_failed`) instead of failing the whole request as a decode error.
 */
@Serializable
data class UpdateGroupRequest(val name: String = "")
