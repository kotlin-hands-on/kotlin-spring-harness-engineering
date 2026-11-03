package com.example.harnessengineeringspring.web

import com.example.harnessengineeringspring.shared.api.ApiJson
import org.springframework.boot.kotlinx.serialization.json.autoconfigure.KotlinxSerializationJsonBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Applies the shared API contract's JSON configuration (design D2) to the backend's HTTP `Json`.
 *
 * Boot 4.1 builds the auto-configured `Json` from `Json.Default` plus every
 * [KotlinxSerializationJsonBuilderCustomizer] bean, and the HTTP message converter uses that instance - so this
 * customizer is the hook that makes the server speak exactly the format the shared contract tests pin. It is not
 * [org.springframework.core.Ordered], which gives it the lowest precedence: it runs after Boot's standard
 * customizer, so the contract wins over any `spring.kotlinx.serialization.json.*` property.
 */
@Configuration(proxyBeanMethods = false)
class ApiJsonConfiguration {
    @Bean
    fun apiContractJsonCustomizer(): KotlinxSerializationJsonBuilderCustomizer =
        KotlinxSerializationJsonBuilderCustomizer { jsonBuilder -> jsonBuilder.apply(ApiJson.contract) }
}
