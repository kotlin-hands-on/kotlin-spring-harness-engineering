package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * Request to create a group (design D3): a non-blank name and the group's fixed ISO 4217 currency code.
 *
 * Both fields default to the empty string so an *absent* field decodes to the domain-level blank or
 * unknown value - which the backend rejects as `validation_failed` - instead of failing the whole
 * request as a decode error; absence means "at its declared default" per the tolerant decoding
 * convention.
 */
@Serializable
data class CreateGroupRequest(val name: String = "", val currency: String = "")
