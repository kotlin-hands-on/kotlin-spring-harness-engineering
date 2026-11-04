package com.example.harnessengineeringspring.group.persistence

import org.jetbrains.exposed.v1.core.Table

/**
 * The `groups` table (design D6): one row per group expense space.
 *
 * The group's only credential is never stored in the clear: [tokenHash] holds the SHA-256 hex digest of
 * the secret access token (design D2), so a database dump does not reveal usable credentials, and the
 * unique index keeps a token lookup a single indexed comparison.
 */
object Groups : Table("groups") {
    /** The group's id, minted at creation; also the target of the members table's `group_id` reference. */
    val id = uuid("id")

    /** The group's display name; free-form and mutable via rename. */
    val name = text("name")

    /** The group's fixed ISO 4217 currency code (canonical form, resolved and validated at creation). */
    val currency = varchar("currency", CURRENCY_CODE_LENGTH)

    /** SHA-256 hex digest of the access token - never the token itself (design D2). */
    val tokenHash = varchar("token_hash", TOKEN_HASH_LENGTH).uniqueIndex()

    override val primaryKey = PrimaryKey(id)

    private const val CURRENCY_CODE_LENGTH = 3
    private const val TOKEN_HASH_LENGTH = 64
}
