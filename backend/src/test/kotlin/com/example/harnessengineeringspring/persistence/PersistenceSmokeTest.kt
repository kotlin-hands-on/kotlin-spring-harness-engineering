package com.example.harnessengineeringspring.persistence

import com.example.harnessengineeringspring.support.IntegrationTest
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@IntegrationTest
class PersistenceSmokeTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val flyway: Flyway,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
    @Autowired private val applicationContext: ApplicationContext,
) {
    @Test
    fun `reports the application as UP with the database attached`() {
        mockMvc.get("/actuator/health").andExpect {
            status { isOk() }
            jsonPath("$.status") { value("UP") }
        }
    }

    @Test
    fun `has applied all migrations and keeps the schema history on the container database`() {
        val info = flyway.info()

        val historyTables = jdbcTemplate.queryForObject(
            "select count(*) from information_schema.tables where table_name = 'flyway_schema_history'",
            Int::class.java,
        )

        assertTrue(info.pending().isEmpty())
        assertTrue(info.all().none { it.state.isFailed })
        assertEquals(1, historyTables)
    }

    @Test
    fun `runs Exposed statements inside a Spring transaction owned by the Exposed transaction manager`() {
        val result = TransactionTemplate(transactionManager).execute {
            TransactionManager.current().exec("SELECT 1") { resultSet ->
                resultSet.next()
                resultSet.getInt(1)
            }
        }

        assertIs<SpringTransactionManager>(transactionManager)
        assertEquals(1, result)
    }

    @Test
    fun `exposes exactly one transaction manager`() {
        val managers = applicationContext.getBeansOfType(PlatformTransactionManager::class.java)

        assertEquals(1, managers.size)
    }
}
