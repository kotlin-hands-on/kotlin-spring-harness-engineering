package com.example.harnessengineeringspring

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.runApplication

/**
 * Exposed's `SpringTransactionManager` (registered by the Exposed starter's auto-configuration) is the single
 * transaction manager, so Boot's datasource one is excluded.
 */
@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])
class HarnessEngineeringSpringApplication

fun main(args: Array<String>) {
    runApplication<HarnessEngineeringSpringApplication>(*args)
}
