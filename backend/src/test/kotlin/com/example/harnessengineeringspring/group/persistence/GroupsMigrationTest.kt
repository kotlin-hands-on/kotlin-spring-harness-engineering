package com.example.harnessengineeringspring.group.persistence

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the applied shape of the generated groups migration (design D2/D6): Flyway must have created the
 * `groups` table, and the group's only credential is protected by a unique index over the token *hash* -
 * the raw token is never stored.
 */
@IntegrationTest
class GroupsMigrationTest(@Autowired private val jdbcTemplate: JdbcTemplate) {
    @Test
    fun `creates the groups table with a unique token hash index`() {
        val groupsTable = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'groups'",
            Int::class.java,
        )
        val uniqueTokenHashIndex = jdbcTemplate.queryForObject(
            """
            select count(*) from pg_indexes
            where tablename = 'groups' and indexdef like 'CREATE UNIQUE INDEX%' and indexdef like '%token_hash%'
            """.trimIndent(),
            Int::class.java,
        )

        assertEquals(1, groupsTable)
        assertEquals(1, uniqueTokenHashIndex)
    }
}
