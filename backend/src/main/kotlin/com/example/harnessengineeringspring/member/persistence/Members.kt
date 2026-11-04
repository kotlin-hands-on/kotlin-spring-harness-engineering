package com.example.harnessengineeringspring.member.persistence

import com.example.harnessengineeringspring.group.persistence.Groups
import org.jetbrains.exposed.v1.core.Table

/**
 * The `members` table (design D6): one row per person inside one group expense space.
 *
 * Members exist only inside their group ([groupId] references `groups.id`); they carry no credentials.
 * There is deliberately no unique constraint on `(group_id, name)` - duplicate member names within a
 * group are allowed (open question 3); the ledger resolves members by id, never by name. The list is
 * ordered by `name, id` for determinism, which the duplicate names make necessary.
 */
object Members : Table("members") {
    /** The member's id, minted at creation; addressable only under its own group's token. */
    val id = uuid("id")

    /** The owning group; a member is invisible under any other group's token. */
    val groupId = uuid("group_id").references(Groups.id)

    /** The member's display name; free-form, mutable, not unique within the group. */
    val name = text("name")

    /** Deactivation state: the default removal path keeps the member listed as inactive. */
    val active = bool("active")

    override val primaryKey = PrimaryKey(id)
}
