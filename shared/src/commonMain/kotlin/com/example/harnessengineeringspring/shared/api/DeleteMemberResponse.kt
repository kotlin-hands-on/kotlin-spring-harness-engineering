package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * The delete-member response on the wire (design D7): a deletion carries no member payload - the
 * member list is the read model - but the api-conventions require every successful write response
 * to include the ledger sequence number the write produced.
 *
 * `seq` is deliberately NOT defaulted: with the contract's `encodeDefaults = false` a defaulted
 * `seq` would be omitted from every deletion response, breaking the api-conventions naming
 * requirement that a write response always carries its `seq`.
 */
@Serializable
data class DeleteMemberResponse(val seq: Long)
