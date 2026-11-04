package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * A group on the wire (design D3): its id, name, fixed ISO 4217 currency code and that currency's
 * default fraction digits, plus the ledger sequence number the response reflects (design D7).
 *
 * `token` is the group's only credential. It is returned exactly once, in the creation response, and
 * defaulted so every read omits it. `seq` is deliberately NOT defaulted: with the contract's
 * `encodeDefaults = false` a defaulted `seq` would be omitted from every response, breaking the
 * api-conventions naming requirement that a response always carries its `seq`.
 */
@Serializable
data class GroupResponse(
    val id: String,
    val name: String,
    val currency: String,
    val fractionDigits: Int,
    val token: String? = null,
    val seq: Long,
)
