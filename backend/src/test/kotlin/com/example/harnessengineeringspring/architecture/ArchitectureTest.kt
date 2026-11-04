package com.example.harnessengineeringspring.architecture

import com.example.harnessengineeringspring.HarnessEngineeringSpringApplication
import com.example.harnessengineeringspring.group.GroupToken
import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.GeneralCodingRules
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import org.jetbrains.exposed.v1.core.Table
import org.springframework.web.bind.annotation.RestController

private const val BASE = "com.example.harnessengineeringspring"

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

    // Controllers talk to services and shared.api DTOs only; the rule was proven to bite once with a
    // temporary violating controller (verify-then-revert, task 9.1 of groups-and-members), and the
    // extension to ..domain.. was proven with a temporary controller referencing a domain generator
    // (verify-then-revert, vertical-slices task 6.3).
    @ArchTest
    val rest_controllers_do_not_depend_on_persistence: ArchRule = noClasses()
        .that().areAnnotatedWith(RestController::class.java)
        .should().dependOnClassesThat().resideInAnyPackage("..persistence..", "..domain..", "org.jetbrains.exposed..")
        .because(
            "controllers are the HTTP boundary; use cases stay behind the slice root service " +
                "(groups-and-members D8, generalized by the vertical-slices ADR)",
        )

    // The Exposed DSL and the table objects stay inside the slices' persistence packages: repositories
    // are the only query surface. The table objects are caught by assignability, so a service cannot
    // even import its own slice's table. Proven to bite once with a temporary slice-root class that
    // queried its slice's table object (verify-then-revert, vertical-slices task 6.1).
    @ArchTest
    val exposed_stays_inside_slice_persistence: ArchRule = noClasses()
        .that().resideOutsideOfPackage("..persistence..")
        .should().dependOnClassesThat().resideInAnyPackage("org.jetbrains.exposed..")
        .orShould().dependOnClassesThat().areAssignableTo(Table::class.java)
        .because(
            "repositories are the only query surface; services never touch the Exposed DSL or table " +
                "objects directly (vertical-slices ADR, decisions D-1/D-3)",
        )

    // Frozen allowlist of shared (non-slice) top-level packages below BASE (vertical-slices ADR, decision 3).
    // Rule 1 builds its shared-target ignore from this list, so every class in web/ and money/ - root and
    // sub-packages alike - is a legal dependency target for all slices; any other top-level package is a
    // slice, its root a legal target via the $BASE.(*) root-ignore, its sub-packages internal. A new
    // top-level package is therefore a new slice unless it is added here, which amends the ADR. The
    // application root package and the :shared module's wire contract are likewise importable by everything.
    private val SHARED_TOPS: Set<String> = ["web", "money"]

    // Cross-slice code may reference another slice's root types only; api/, domain/, application/ and
    // persistence/ are internal to their slice. The shared zones of SHARED_TOPS (web, money) are legal
    // targets wholesale - root and sub-packages - via the ignore clause the rule derives from that
    // allowlist. The FK exception: a table object may reference another slice's table object for schema
    // definition (`Members.groupId references Groups.id`) - allowed for tables, not for DSL. The legal
    // edges (member -> group root types, web -> group root, anything -> money/shared.api, root service ->
    // own repository) stay green. Proven to bite in both directions with temporary violations - a member
    // class importing group.domain, a group-root class importing member.persistence, and a
    // member/persistence non-table class querying the foreign groups table (verify-then-revert,
    // vertical-slices task 6.2).
    @ArchTest
    val cross_slice_access_goes_through_the_slice_root: ArchRule = slices()
        .matching("$BASE.(*)..")
        .should().notDependOnEachOther()
        .ignoreDependency(alwaysTrue(), resideInAPackage("$BASE.(*)"))
        .ignoreDependency(alwaysTrue(), resideInAPackage("$BASE.shared.."))
        .ignoreDependency(alwaysTrue(), resideInAnyPackage(*SHARED_TOPS.map { "$BASE.$it.." }.toTypedArray()))
        .ignoreDependency(assignableTo(Table::class.java), assignableTo(Table::class.java))
        .because(
            "a slice's public API is its root package; api/, domain/, application/, persistence/ are " +
                "internal, except that a table object may reference another slice's table object for an " +
                "FK definition (vertical-slices ADR, decisions D-2/D-3)",
        )

    // Within a slice, only api/ may depend on the shared web package; web's own classes are exempt.
    // Proven to bite once with a temporary GroupService-level reference of web.ApiException
    // (verify-then-revert, vertical-slices task 6.3).
    @ArchTest
    val only_slice_api_depends_on_web: ArchRule = noClasses()
        .that().resideOutsideOfPackage("..api..")
        .and().resideOutsideOfPackage("$BASE.web..")
        .should().dependOnClassesThat().resideInAPackage("$BASE.web..")
        .because(
            "HTTP plumbing is an adapter concern; within a slice only api/ touches it (vertical-slices ADR, D-1)",
        )

    // Controllers receive the resolver-verified group context, never a raw token; the probe controller is
    // test code and invisible to this analysis. Proven to bite once with a temporary @RestController
    // constructing a GroupToken (verify-then-revert, vertical-slices task 6.3).
    @ArchTest
    val controllers_never_touch_group_tokens: ArchRule = noClasses()
        .that().areAnnotatedWith(RestController::class.java)
        .should().dependOnClassesThat(equivalentTo(GroupToken::class.java))
        .because(
            "the web resolver resolves the path token; controllers receive ResolvedGroup (vertical-slices ADR, D-4)",
        )

    // No application/ package exists yet, so this may check zero classes (same convention as
    // no_field_injection above); it guards the anatomy from day one. Proven to bite once with a
    // temporary application/ service plus a persistence/ class that called it
    // (verify-then-revert, vertical-slices task 6.4).
    @ArchTest
    val application_services_are_called_only_by_root_services: ArchRule = noClasses()
        .that().resideInAnyPackage("..api..", "..domain..", "..persistence..")
        .should().dependOnClassesThat().resideInAPackage("..application..")
        .allowEmptyShould(true)
        .because(
            "application services exist to keep root services focused; only the slice's root services " +
                "may call them (vertical-slices ADR, decision D-1)",
        )
}
