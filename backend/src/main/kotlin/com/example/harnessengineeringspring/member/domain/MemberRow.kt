package com.example.harnessengineeringspring.member.domain

import com.example.harnessengineeringspring.member.MemberId

/**
 * A member row as the persistence layer reads it back (vertical-slices ADR): the member's identity, name
 * and active state. Internal to the slice - the root service maps it onto the wire response; persistence
 * never touches the shared API contract.
 */
data class MemberRow(val id: MemberId, val name: String, val active: Boolean)
