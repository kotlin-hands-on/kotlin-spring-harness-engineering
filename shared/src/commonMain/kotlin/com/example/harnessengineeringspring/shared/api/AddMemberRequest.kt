package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * Request to add a member to the addressed group (design D3): a non-blank name. Members carry no
 * credentials; they exist only inside their group.
 *
 * `name` defaults to the empty string so an *absent* name decodes to the domain-level blank rejection
 * (`validation_failed`) instead of failing the whole request as a decode error.
 */
@Serializable
data class AddMemberRequest(val name: String = "")
