package com.example.harnessengineeringspring.persistence

import com.example.harnessengineeringspring.support.IntegrationTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the applied shape of the generated members migration (design D6): Flyway must have created the
 * `members` table with its reference back to `groups(id)`, and deliberately without any unique
 * constraint - duplicate member names within a group are allowed (open question 3).
 */
@IntegrationTest
class MembersMigrationTest(@Autowired private val jdbcTemplate: JdbcTemplate) {
    @Test
    fun `creates the members table referencing groups without a unique name constraint`() {
        val membersTable = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'members'",
            Int::class.java,
        )
        val groupReference = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.key_column_usage referencing
            join information_schema.constraint_column_usage referenced
              on referencing.constraint_name = referenced.constraint_name
            where referencing.table_name = 'members'
              and referenced.table_name = 'groups'
              and referenced.column_name = 'id'
            """.trimIndent(),
            Int::class.java,
        )
        val uniqueConstraints = jdbcTemplate.queryForObject(
            """
            select count(*)
            from information_schema.table_constraints
            where constraint_type = 'UNIQUE' and table_name = 'members'
            """.trimIndent(),
            Int::class.java,
        )

        assertEquals(1, membersTable)
        assertEquals(1, groupReference)
        assertEquals(0, uniqueConstraints)
    }
}
