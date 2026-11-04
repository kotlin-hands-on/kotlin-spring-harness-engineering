package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * Request to update a member by its id within the addressed group (design D3). PATCH semantics: an
 * absent field means "leave the corresponding property unchanged", so both fields are nullable with a
 * null default - a null is omitted from the JSON and decodes back to null.
 *
 * Deactivation is the default removal path: `active: false` keeps the member in the list as inactive
 * and `active: true` reactivates it later.
 */
@Serializable
data class UpdateMemberRequest(val name: String? = null, val active: Boolean? = null)
