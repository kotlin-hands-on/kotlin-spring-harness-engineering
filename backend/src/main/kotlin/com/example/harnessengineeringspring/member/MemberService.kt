package com.example.harnessengineeringspring.member

import com.example.harnessengineeringspring.group.ResolvedGroup
import com.example.harnessengineeringspring.member.domain.MemberIdGenerator
import com.example.harnessengineeringspring.member.persistence.MemberDeletion
import com.example.harnessengineeringspring.member.persistence.MemberRepository
import com.example.harnessengineeringspring.shared.api.AddMemberRequest
import com.example.harnessengineeringspring.shared.api.MemberListResponse
import com.example.harnessengineeringspring.shared.api.MemberResponse
import com.example.harnessengineeringspring.shared.api.UpdateMemberRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The member use cases (design D1/D4): plain-CRUD member state behind sealed results - member changes
 * are never ledger events. Every query is scoped to the resolver-verified group, so a member of
 * another group is indistinguishable from an unknown id. Like the group endpoints, every response
 * reports the group's ledger head, a constant until `ledger-core` wires it (design D7).
 */
@Service
class MemberService(private val ids: MemberIdGenerator, private val repository: MemberRepository) {

    /** Adds an active member to the addressed group; duplicate names within the group are allowed. */
    @Transactional
    fun add(resolved: ResolvedGroup, request: AddMemberRequest): AddMember = when {
        request.name.isBlank() -> AddMember.InvalidName
        else -> insertMember(request, resolved)
    }

    /**
     * Lists every member of the addressed group - active and deactivated - ordered by name, then id
     * for determinism where names repeat.
     */
    @Transactional(readOnly = true)
    fun list(resolved: ResolvedGroup): MemberListResponse = MemberListResponse(
        members = membersOf(resolved),
        seq = CURRENT_LEDGER_HEAD,
    )

    /**
     * Updates a member by its id within the addressed group (design D3/D4). PATCH semantics: an absent
     * field leaves the property unchanged; a present blank name is rejected. Deactivation is the
     * default removal path - an inactive member stays listed and can be reactivated. The scoped
     * lookup makes a member of another group indistinguishable from an unknown id.
     */
    @Transactional
    fun update(resolved: ResolvedGroup, memberId: MemberId, request: UpdateMemberRequest): UpdateMember {
        val name = request.name
        if (name != null && name.isBlank()) return UpdateMember.InvalidName
        return updateMember(resolved = resolved, memberId = memberId, request = request)
    }

    /**
     * Hard-deletes a member of the addressed group (design D6). Whether the member is active or
     * deactivated makes no difference here. The database's `ON DELETE RESTRICT` guard is the
     * enforcement mechanism for the "referenced by no ledger event" rule: the repository reports the
     * referential-integrity violation back, and it is translated into [DeleteMember.Referenced] so
     * the caller can reject with `conflict`.
     */
    @Transactional
    fun delete(resolved: ResolvedGroup, memberId: MemberId): DeleteMember {
        val deletion = repository.deleteScoped(resolved.group.id, memberId)
        return when (deletion) {
            MemberDeletion.DELETED -> DeleteMember.Deleted(seq = CURRENT_LEDGER_HEAD)
            MemberDeletion.NOT_FOUND -> DeleteMember.NotFound
            MemberDeletion.REFERENCED -> DeleteMember.Referenced
        }
    }

    private companion object {
        /** The group's ledger head while no ledger exists (design D7); `ledger-core` owns wiring the real value. */
        const val CURRENT_LEDGER_HEAD = 0L
    }

    private fun updateMember(resolved: ResolvedGroup, memberId: MemberId, request: UpdateMemberRequest): UpdateMember {
        repository.update(
            groupId = resolved.group.id,
            memberId = memberId,
            name = request.name,
            active = request.active,
        )
        val row = repository.selectScoped(resolved.group.id, memberId) ?: return UpdateMember.NotFound
        return UpdateMember.Updated(
            MemberResponse(
                id = row.id.value.toString(),
                name = row.name,
                active = row.active,
                seq = CURRENT_LEDGER_HEAD,
            ),
        )
    }

    private fun insertMember(request: AddMemberRequest, resolved: ResolvedGroup): AddMember.Added {
        val id = ids.next()
        repository.insert(id = id, groupId = resolved.group.id, name = request.name)
        return AddMember.Added(
            MemberResponse(id = id.value.toString(), name = request.name, active = true, seq = CURRENT_LEDGER_HEAD),
        )
    }

    private fun membersOf(resolved: ResolvedGroup): List<MemberResponse> =
        repository.membersOf(resolved.group.id).map { row ->
            MemberResponse(
                id = row.id.value.toString(),
                name = row.name,
                active = row.active,
                seq = CURRENT_LEDGER_HEAD,
            )
        }
}
