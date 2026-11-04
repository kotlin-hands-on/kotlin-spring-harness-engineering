@file:MustUseReturnValues

package com.example.harnessengineeringspring.member.persistence

import com.example.harnessengineeringspring.group.GroupId
import com.example.harnessengineeringspring.member.MemberId
import com.example.harnessengineeringspring.member.domain.MemberRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository
import java.sql.SQLException

/**
 * The member slice's only query surface (vertical-slices ADR): every Exposed statement that touches the
 * [Members] table lives here. A plain bean without a transaction boundary of its own - the calling root
 * service owns the transaction (design D-D). [MustUseReturnValues] (on this file) makes the compiler
 * report a dropped query result - ignoring a [deleteScoped] verdict would silently swallow a
 * [MemberDeletion.NOT_FOUND] or [MemberDeletion.REFERENCED] outcome.
 */
@Repository
class MemberRepository {

    /**
     * Inserts a new member row; the caller mints the id. Members start active - deactivation is the
     * reversible removal path and only ever an update (design D3).
     */
    fun insert(id: MemberId, groupId: GroupId, name: String) {
        Members.insert {
            it[Members.id] = id.value
            it[Members.groupId] = groupId.value
            it[Members.name] = name
            it[Members.active] = true
        }
    }

    /**
     * Updates a member scoped to [groupId], or does nothing when the request carries no change. Absent
     * fields leave the property unchanged (PATCH semantics, design D3) - a blank name is rejected by the
     * caller before it gets here.
     */
    fun update(groupId: GroupId, memberId: MemberId, name: String?, active: Boolean?) {
        if (name == null && active == null) return
        val scope = (Members.id eq memberId.value) and (Members.groupId eq groupId.value)
        Members.update({ scope }) {
            name?.let { value -> it[Members.name] = value }
            active?.let { value -> it[Members.active] = value }
        }
    }

    /**
     * Reads a member back scoped to [groupId], or null. The scoped lookup makes a member of another group
     * indistinguishable from an unknown id (design D3/D4).
     */
    fun selectScoped(groupId: GroupId, memberId: MemberId): MemberRow? {
        val scope = (Members.id eq memberId.value) and (Members.groupId eq groupId.value)
        val row = Members
            .select(Members.id, Members.name, Members.active)
            .where { scope }
            .singleOrNull()
            ?: return null
        return MemberRow(id = MemberId(row[Members.id]), name = row[Members.name], active = row[Members.active])
    }

    /**
     * Hard-deletes a member scoped to [groupId] (design D6). Whether the member is active or deactivated
     * makes no difference. The database's `ON DELETE RESTRICT` guard is the enforcement mechanism for the
     * "referenced by no ledger event" rule, so a referential-integrity violation is reported as
     * [MemberDeletion.REFERENCED] for the caller to reject with `conflict`.
     */
    fun deleteScoped(groupId: GroupId, memberId: MemberId): MemberDeletion {
        val scope = (Members.id eq memberId.value) and (Members.groupId eq groupId.value)
        return try {
            val deleted = Members.deleteWhere { scope }
            if (deleted == 1) MemberDeletion.DELETED else MemberDeletion.NOT_FOUND
        } catch (exception: ExposedSQLException) {
            if (exception.isReferentialIntegrityViolation()) MemberDeletion.REFERENCED else throw exception
        }
    }

    /** Lists every member of the group - active and deactivated - ordered by name, then id for determinism. */
    fun membersOf(groupId: GroupId): List<MemberRow> = Members
        .select(Members.id, Members.name, Members.active)
        .where { Members.groupId eq groupId.value }
        .orderBy(Members.name to SortOrder.ASC, Members.id to SortOrder.ASC)
        .map { row ->
            MemberRow(
                id = MemberId(row[Members.id]),
                name = row[Members.name],
                active = row[Members.active],
            )
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
        /** The SQLStates a foreign-key/RESTRICT violation surfaces as, server- and driver-side. */
        val REFERENTIAL_INTEGRITY_VIOLATIONS: Set<String> = ["23001", "23503"]
    }
}

/**
 * The persistence-observable outcome of a scoped member deletion: the row was removed, matched no
 * member of the group, or was blocked by the `ON DELETE RESTRICT` guard. The root service maps it onto
 * its sealed use-case result; the ledger-head `seq` is not persistence knowledge.
 */
@MustUseReturnValues
enum class MemberDeletion {
    DELETED,
    NOT_FOUND,
    REFERENCED,
}
