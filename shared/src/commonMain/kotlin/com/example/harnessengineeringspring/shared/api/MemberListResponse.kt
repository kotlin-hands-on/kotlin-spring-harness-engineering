package com.example.harnessengineeringspring.shared.api

import kotlinx.serialization.Serializable

/**
 * The member list of the addressed group on the wire (design D3): every member - active and
 * deactivated - in name order, plus the ledger sequence number the list reflects (design D7).
 */
@Serializable
data class MemberListResponse(val members: List<MemberResponse>, val seq: Long)
