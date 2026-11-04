package com.example.harnessengineeringspring.member.api

import com.example.harnessengineeringspring.group.ResolvedGroup
import com.example.harnessengineeringspring.member.AddMember
import com.example.harnessengineeringspring.member.DeleteMember
import com.example.harnessengineeringspring.member.MemberId
import com.example.harnessengineeringspring.member.MemberService
import com.example.harnessengineeringspring.member.UpdateMember
import com.example.harnessengineeringspring.shared.api.AddMemberRequest
import com.example.harnessengineeringspring.shared.api.ApiError
import com.example.harnessengineeringspring.shared.api.DeleteMemberResponse
import com.example.harnessengineeringspring.shared.api.MemberListResponse
import com.example.harnessengineeringspring.shared.api.MemberResponse
import com.example.harnessengineeringspring.shared.api.UpdateMemberRequest
import com.example.harnessengineeringspring.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * The member HTTP boundary (design D1/D4): the only place that maps a member use-case result to
 * an HTTP response or an [ApiException]. Every route is nested under the group's token segment
 * (api-conventions); the web resolver verifies that token into a [ResolvedGroup] before any handler
 * runs, so the token never appears in a handler, a body or a log line.
 */
@RestController
@RequestMapping("/api/v1/groups/{token}/members")
class MemberController(private val members: MemberService) {

    /** Adds a member to the addressed group; 201 with the active member. */
    @PostMapping
    fun add(resolved: ResolvedGroup, @RequestBody request: AddMemberRequest): ResponseEntity<MemberResponse> =
        when (val result = members.add(resolved, request)) {
            is AddMember.Added -> ResponseEntity.status(HttpStatus.CREATED).body(result.member)

            is AddMember.InvalidName ->
                throw ApiException(ApiError.ValidationFailed(message = "member name must not be blank"))
        }

    /**
     * Lists the members of the addressed group. The `atLeastSeq` parameter is accepted per the
     * api-conventions naming; its waiting semantics belong to `ledger-core`.
     */
    @GetMapping
    fun list(
        resolved: ResolvedGroup,
        @Suppress("UnusedParameter") @RequestParam atLeastSeq: Long?,
    ): MemberListResponse = members.list(resolved)

    /**
     * Updates a member of the addressed group by its id. PATCH semantics (design D3): an absent field
     * leaves the property unchanged, a present blank name is rejected, and deactivation is reversible.
     * A malformed id is treated exactly like an unknown one - indistinguishable `not_found`.
     */
    @PatchMapping("/{memberId}")
    fun update(
        resolved: ResolvedGroup,
        @PathVariable memberId: String,
        @RequestBody request: UpdateMemberRequest,
    ): MemberResponse {
        val id = MemberId.parse(memberId) ?: noSuchMember()
        return when (val result = members.update(resolved = resolved, memberId = id, request = request)) {
            is UpdateMember.Updated -> result.member

            is UpdateMember.InvalidName ->
                throw ApiException(ApiError.ValidationFailed(message = "member name must not be blank"))

            is UpdateMember.NotFound -> noSuchMember()
        }
    }

    /**
     * Hard-deletes a member of the addressed group; 200 carrying the write's `seq`, the
     * api-conventions body every successful write returns (design D7) - a deletion has no member
     * payload, the member list is the read model. A malformed id is treated exactly like an unknown
     * one (design D6). A member still referenced by a ledger event is rejected as `conflict`
     * (design D6).
     */
    @DeleteMapping("/{memberId}")
    fun delete(resolved: ResolvedGroup, @PathVariable memberId: String): DeleteMemberResponse {
        val id = MemberId.parse(memberId) ?: noSuchMember()
        return when (val result = members.delete(resolved, id)) {
            is DeleteMember.Deleted -> DeleteMemberResponse(seq = result.seq)

            DeleteMember.Referenced ->
                throw ApiException(ApiError.Conflict(message = "member is referenced by a ledger event"))

            DeleteMember.NotFound -> noSuchMember()
        }
    }

    private fun noSuchMember(): Nothing = throw ApiException(ApiError.NotFound(message = "no such member"))
}
