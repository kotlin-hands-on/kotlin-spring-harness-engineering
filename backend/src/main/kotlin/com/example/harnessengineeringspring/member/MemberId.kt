package com.example.harnessengineeringspring.member

import kotlin.uuid.Uuid

/**
 * A member's identity (design D3): distinct from [com.example.harnessengineeringspring.group.GroupId]
 * so ids cannot be mixed up at the service boundary. A member is addressable only under its own
 * group's token; the wire form is the UUID's hex-and-dash string.
 */
@JvmInline
value class MemberId(val value: Uuid) {
    companion object {
        /**
         * Parses a wire id, or null when malformed. Callers treat a malformed id exactly like an
         * unknown one - indistinguishable `not_found` - matching the token rule (design D2).
         */
        fun parse(raw: String): MemberId? = Uuid.parseOrNull(raw)?.let(::MemberId)
    }
}
