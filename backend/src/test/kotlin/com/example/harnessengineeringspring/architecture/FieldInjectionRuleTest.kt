package com.example.harnessengineeringspring.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.library.GeneralCodingRules
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Proves the field-injection rule is not vacuous: production code has no fields yet, so [ArchitectureTest] may check
 * zero classes, but the rule itself must still reject a class that injects a field.
 */
class FieldInjectionRuleTest {
    @Test
    fun `field injection rule rejects a class that injects a field`() {
        val classes = ClassFileImporter().importClasses(FieldInjectedFixture::class.java)

        assertFailsWith<AssertionError> {
            GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.check(classes)
        }
    }
}

private class FieldInjectedFixture {
    @Autowired
    @Suppress("unused")
    lateinit var dependency: String
}
