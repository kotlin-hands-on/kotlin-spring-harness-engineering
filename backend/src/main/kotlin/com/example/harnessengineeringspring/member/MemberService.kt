package com.example.harnessengineeringspring.member

import com.example.harnessengineeringspring.group.GroupLookup
import com.example.harnessengineeringspring.group.GroupService
import com.example.harnessengineeringspring.group.GroupToken
import com.example.harnessengineeringspring.persistence.Members
import com.example.harnessengineeringspring.shared.api.AddMemberRequest
import com.example.harnessengineeringspring.shared.api.MemberListResponse
import com.example.harnessengineeringspring.shared.api.MemberResponse
import com.example.harnessengineeringspring.shared.api.UpdateMemberRequest
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.SQLException
import kotlin.uuid.Uuid

/**
 * The member use cases (design D1/D4): plain-CRUD member state behind sealed results - member changes
 * are never ledger events. Every query is scoped to the group addressed by the token, so a member of
 * another group is indistinguishable from an unknown id. Like the group endpoints, every response
 * reports the group's ledger head, a constant until `ledger-core` wires it (design D7).
 */
@Service
class MemberService(private val groups: GroupService, private val ids: MemberIdGenerator) {

    /** Adds an active member to the addressed group; duplicate names within the group are allowed. */
    @Transactional
    fun add(token: GroupToken, request: AddMemberRequest): AddMember = when {
        request.name.isBlank() -> AddMember.InvalidName

        else -> when (val found = groups.byToken(token)) {
            is GroupLookup.NotFound -> AddMember.GroupNotFound
            is GroupLookup.Found -> insertMember(request, found.group.id.value)
        }
    }

    /**
     * Lists every member of the addressed group - active and deactivated - ordered by name, then id
     * for determinism where names repeat.
     */
    @Transactional(readOnly = true)
    fun list(token: GroupToken): MemberList = when (val found = groups.byToken(token)) {
        is GroupLookup.NotFound -> MemberList.GroupNotFound

        is GroupLookup.Found -> MemberList.Listed(
            MemberListResponse(
                members = membersOf(found.group.id.value),
                seq = CURRENT_LEDGER_HEAD,
            ),
        )
    }

    /**
     * Updates a member by its id within the addressed group (design D3/D4). PATCH semantics: an absent
     * field leaves the property unchanged; a present blank name is rejected. Deactivation is the
     * default removal path - an inactive member stays listed and can be reactivated. The scoped
     * lookup makes a member of another group indistinguishable from an unknown id.
     */
    @Transactional
    fun update(token: GroupToken, memberId: MemberId, request: UpdateMemberRequest): UpdateMember {
        val name = request.name
        if (name != null && name.isBlank()) return UpdateMember.InvalidName
        return when (val found = groups.byToken(token)) {
            is GroupLookup.NotFound -> UpdateMember.NotFound
            is GroupLookup.Found -> updateMember(groupId = found.group.id.value, memberId = memberId, request = request)
        }
    }

    /**
     * Hard-deletes a member of the addressed group (design D6). Whether the member is active or
     * deactivated makes no difference here. The database's `ON DELETE RESTRICT` guard is the
     * enforcement mechanism for the "referenced by no ledger event" rule: a referential-integrity
     * violation is translated into [DeleteMember.Referenced] so the caller can reject with `conflict`.
     */
    @Transactional
    fun delete(token: GroupToken, memberId: MemberId): DeleteMember {
        val group = when (val found = groups.byToken(token)) {
            is GroupLookup.NotFound -> return DeleteMember.NotFound
            is GroupLookup.Found -> found.group
        }
        return try {
            val deleted = Members.deleteWhere { (Members.id eq memberId.value) and (Members.groupId eq group.id.value) }
            if (deleted == 1) DeleteMember.Deleted(seq = CURRENT_LEDGER_HEAD) else DeleteMember.NotFound
        } catch (exception: ExposedSQLException) {
            if (exception.isReferentialIntegrityViolation()) DeleteMember.Referenced else throw exception
        }
    }

    /**
     * A referential-integrity violation: some row still references this one. The PostgreSQL server
     * reports a foreign-key RESTRICT violation as SQLState 23503, and pgjdbc passes the server's
     * SQLState through unchanged. 23001, the SQL standard's restrict-violation code, is kept in the
     * set defensively so a driver that translates the server's state to the standard code also maps
     * - a member deletion can only hit either through a reference to the member.
     */
    private fun ExposedSQLException.isReferentialIntegrityViolation(): Boolean =
        generateSequence<Throwable>(this) { it.cause }
            .any { it is SQLException && it.sqlState in REFERENTIAL_INTEGRITY_VIOLATIONS }

    private companion object {
        /** The group's ledger head while no ledger exists (design D7); `ledger-core` owns wiring the real value. */
        const val CURRENT_LEDGER_HEAD = 0L

        /** The SQLStates a foreign-key/RESTRICT violation surfaces as, server- and driver-side. */
        val REFERENTIAL_INTEGRITY_VIOLATIONS: Set<String> = ["23001", "23503"]
    }

    private fun updateMember(groupId: Uuid, memberId: MemberId, request: UpdateMemberRequest): UpdateMember {
        val scope = (Members.id eq memberId.value) and (Members.groupId eq groupId)
        val hasChanges = request.name != null || request.active != null
        if (hasChanges) {
            Members.update({ scope }) {
                request.name?.let { name -> it[Members.name] = name }
                request.active?.let { active -> it[Members.active] = active }
            }
        }
        val row = Members.select(Members.id, Members.name, Members.active)
            .where { scope }
            .singleOrNull()
            ?: return UpdateMember.NotFound
        return UpdateMember.Updated(
            MemberResponse(
                id = row[Members.id].toString(),
                name = row[Members.name],
                active = row[Members.active],
                seq = CURRENT_LEDGER_HEAD,
            ),
        )
    }

    private fun insertMember(request: AddMemberRequest, groupId: Uuid): AddMember.Added {
        val id = ids.next()
        Members.insert {
            it[Members.id] = id.value
            it[Members.groupId] = groupId
            it[Members.name] = request.name
            it[Members.active] = true
        }
        return AddMember.Added(
            MemberResponse(id = id.value.toString(), name = request.name, active = true, seq = CURRENT_LEDGER_HEAD),
        )
    }

    private fun membersOf(groupId: Uuid): List<MemberResponse> =
        Members.select(Members.id, Members.name, Members.active)
            .where { Members.groupId eq groupId }
            .orderBy(Members.name to SortOrder.ASC, Members.id to SortOrder.ASC)
            .map { row ->
                MemberResponse(
                    id = row[Members.id].toString(),
                    name = row[Members.name],
                    active = row[Members.active],
                    seq = CURRENT_LEDGER_HEAD,
                )
            }
}
