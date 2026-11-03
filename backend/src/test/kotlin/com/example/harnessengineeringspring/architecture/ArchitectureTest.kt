package com.example.harnessengineeringspring.architecture

import com.example.harnessengineeringspring.HarnessEngineeringSpringApplication
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules

@AnalyzeClasses(
    packagesOf = [HarnessEngineeringSpringApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
// Rule names are snake_case on purpose: the field name becomes the test display name (archunit.properties).
@Suppress("VariableNaming", "PropertyName")
class ArchitectureTest {
    @ArchTest
    val no_production_code_uses_jackson: ArchRule = noClasses()
        .should().dependOnClassesThat().resideInAnyPackage("com.fasterxml.jackson..", "tools.jackson..")
        .because(
            "application JSON goes through kotlinx.serialization; Jackson stays on the classpath for Actuator only",
        )

    @ArchTest
    val persistence_uses_exposed_dsl_only: ArchRule = noClasses()
        .should().dependOnClassesThat().resideInAnyPackage("org.jetbrains.exposed.v1.dao..")
        .because("persistence uses the Exposed DSL only; the DAO API is not part of the stack")

    // No production class has fields yet, so this may check zero classes; FieldInjectionRuleTest proves it can fail.
    @ArchTest
    val no_field_injection: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true)

    // No ledger or persistence class exists yet, so this may check zero classes; task 7.1 proved it bites once
    // with a temporary violating class (verify-then-revert, same as build-foundation A1).
    @ArchTest
    val ledger_and_persistence_do_not_depend_on_shared_api: ArchRule = noClasses()
        .that().resideInAnyPackage("..ledger..", "..persistence..")
        .should().dependOnClassesThat().resideInAPackage("..shared.api..")
        .allowEmptyShould(true)
        .because(
            "wire DTOs in ..shared.api.. are the API contract; ledger event payloads and persistence types are " +
                "separate types even where their JSON shapes coincide (design D7)",
        )
}
