@file:MustUseReturnValues

package com.example.harnessengineeringspring.group.persistence

import com.example.harnessengineeringspring.group.Group
import com.example.harnessengineeringspring.group.GroupId
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.springframework.stereotype.Repository

/**
 * The group slice's only query surface (vertical-slices ADR): every Exposed statement that touches the
 * [Groups] table lives here. A plain bean without a transaction boundary of its own - the calling root
 * service owns the transaction (design D-D). [MustUseReturnValues] (on this file) makes the compiler
 * report a dropped query result - ignoring [findByTokenHash]'s null would silently treat an unknown
 * token as matched.
 */
@Repository
class GroupRepository {

    /** Inserts a new group row; the caller mints the id and the token's digest (design D2/D3). */
    fun insert(id: GroupId, name: String, currency: String, tokenHash: String) {
        Groups.insert {
            it[Groups.id] = id.value
            it[Groups.name] = name
            it[Groups.currency] = currency
            it[Groups.tokenHash] = tokenHash
        }
    }

    /**
     * Finds the group whose stored token digest matches, or null. The digest is looked up in the unique
     * index, so an unknown and a malformed token are indistinguishable by construction (design D2).
     */
    fun findByTokenHash(tokenHash: String): Group? {
        val row = Groups
            .select(Groups.id, Groups.name, Groups.currency)
            .where { Groups.tokenHash eq tokenHash }
            .singleOrNull()
            ?: return null
        return Group(id = GroupId(row[Groups.id]), name = row[Groups.name], currency = row[Groups.currency])
    }

    /** Updates a group's display name; the id and the currency fixed at creation never change (design D5). */
    fun updateName(id: GroupId, name: String) {
        Groups.update({ Groups.id eq id.value }) { it[Groups.name] = name }
    }
}
