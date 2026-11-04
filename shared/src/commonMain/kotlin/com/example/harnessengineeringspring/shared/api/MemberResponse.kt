package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * A member on the wire (design D3): its id, name and active state, plus the ledger sequence number
 * the response reflects (design D7).
 *
 * `active` and `seq` are deliberately NOT defaulted: with the contract's `encodeDefaults = false` a
 * defaulted `active` or `seq` would be omitted whenever it equals the default - an inactive member
 * would lose its `active: false` and every response would lose `seq` - so both are always emitted.
 */
@Serializable
data class MemberResponse(val id: String, val name: String, val active: Boolean, val seq: Long)
