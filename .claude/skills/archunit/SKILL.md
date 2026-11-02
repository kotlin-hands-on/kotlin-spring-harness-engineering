---
name: archunit
description: Architecture tests with ArchUnit 1.5 (archunit-junit6) in Kotlin/Spring - layered/onion architecture, package dependency and cycle rules, naming/annotation/Spring conventions, custom ArchConditions, freezing existing violations. Use when adding, writing, reviewing or fixing ArchUnit tests, turning an architecture decision or a review finding into an executable rule, or when an `ArchitectureTest` fails.
---

# ArchUnit - architecture rules as tests

ArchUnit imports compiled bytecode (`build/classes/...`) and checks rules on it: which packages may depend on which, which layers may access which, no cycles, naming and annotation conventions. A failing rule throws an `AssertionError` that lists every violation with its class and line.

Prerequisites: `kotlin-testing` (JUnit 6 stack). Companion skills: `tdd` (a new rule follows red → green too), `change-review` (the `architecture` focus can propose rules), `spring-testing`.

Note: architecture tests are the one deliberate **white-box** test kind. They check structure, not behaviour, so the `tdd` rule "test through the public interface" does not apply to them. They never replace behaviour tests.

## Setup check (run first)

1. **Dependency.** `testImplementation("com.tngtech.archunit:archunit-junit6:1.5.1")`, which brings both the API and the `archunit` TestEngine. Use `archunit-junit5` only for JUnit 5 projects; there is no JUnit 4 runner here. Check with the Gradle MCP `inspect_dependencies` on `testRuntimeClasspath`.
2. **Engine runs.** `useJUnitPlatform()` is enough. If the build restricts engines (`includeEngines(...)`), add `"archunit"`.
3. **Existing tests?** Search `src/test` for `@AnalyzeClasses` and add to the existing architecture test class instead of creating a second one.
4. **Config.** Look for `src/test/resources/archunit.properties` (freezing, display names, empty-should settings) → `references/setup-and-config.md`.

## Minimal Kotlin test

```kotlin
@AnalyzeClasses(
    packagesOf = [HarnessEngineeringSpringApplication::class],
    importOptions = [ImportOption.DoNotIncludeTests::class],
)
class ArchitectureTest {

    @ArchTest
    val services_must_not_depend_on_web: ArchRule = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat().resideInAPackage("..web..")
        .because("web is an adapter; the service layer must stay usable without HTTP")
}
```

## MUST DO

- **One architecture test class per module**, in the root package of the code under test: `src/test/kotlin/<root>/architecture/ArchitectureTest.kt`. Group large rule sets in plain classes and pull them in with ``@ArchTest val web: ArchTests = ArchTests.`in`(WebRules::class.java)`` (`in` is a Kotlin keyword, hence the backticks).
- **Import production code only**: `importOptions = [ImportOption.DoNotIncludeTests::class]`. Point `packagesOf` at a class (refactor-safe), not at a `packages = ["..."]` string.
- **Rules as `@ArchTest val` fields of type `ArchRule`.** Use `@ArchTest fun rule(classes: JavaClasses)` only when a rule needs the imported classes programmatically.
- **`.because("...")` on every rule** that isn't self-explanatory. The reason ends up in the failure message and is the only documentation the next developer sees.
- **snake_case rule names**, plus `junit.displayName.replaceUnderscoresBySpaces=true` in `archunit.properties`. This is an exception to `kotlin-testing`'s backtick names, because the field name becomes the test name.
- **Allow Kotlin/Java std deps** in whitelist rules (`onlyDependOnClassesThat`): every Kotlin class depends on `kotlin..` (e.g. `kotlin.jvm.internal.Intrinsics`) and `java..`.
- **Prove a new rule bites (red first):** before relying on it, check it fails on a deliberate violation (temporarily add one or point the rule at a known offender), then revert. A rule that never fails is worthless.
- **Prefer library rules** (`layeredArchitecture()`, `onionArchitecture()`, `slices()`, `GeneralCodingRules`) over hand-written conditions.

## MUST NOT DO

- **Don't use `@Disabled`**, because it has no effect on `@ArchTest`. Use `@ArchIgnore(reason = "...")`, and only temporarily.
- **Don't silence a failing rule** by widening its `that()` clause, adding `allowEmptyShould(true)` or adding an ignore pattern just to get green. Fix the code, or **freeze** the rule deliberately (`references/setup-and-config.md`) and say so.
- **Don't write `..SomeClass` package patterns.** Patterns match the **package** name only. To match classes, use `haveSimpleName(...)` or `haveNameMatching(...)`.
- **Don't check Kotlin `internal`** with ArchUnit: in bytecode `internal` is `public`. Enforce module boundaries with packages, rules, or Gradle modules.
- **Don't new up `ClassFileImporter` in every test.** `@AnalyzeClasses` caches the import across rules and classes.
- **Don't assert behaviour** (return values, HTTP status) in ArchUnit tests. That is the job of the behaviour tests.

## SHOULD DO

- **Start small and grow**: dependency direction + `slices().matching("<root>.(*)..").should().beFreeOfCycles()` + `GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION` give the most value per line. In a fresh project these fail with "failed to check any classes" until there are subpackages and classes with fields, so add each rule together with the first code it guards.
- **Encode decisions when they are made**: an ADR, an OpenSpec `design.md` decision or a `change-review` architecture finding → a rule in the same change.
- **`ensureAllClassesAreContainedInArchitecture()`** on layered/onion rules, so new classes can't slip outside every layer unnoticed.
- **`optionalLayer(...)` / `withOptionalLayers(true)`** for layers the code doesn't have yet. Otherwise an empty layer fails the rule, which is intentional (`archRule.failOnEmptyShould=true` by default).
- **Exclude compiler-generated classes** in naming rules: `areTopLevelClasses()`, `areNotAnonymousClasses()`, or skip `.*Kt` file facades and `\$Companion` / `\$WhenMappings` / `\$DefaultImpls` names.

## References

| Load when | File |
|---|---|
| Writing rules - package patterns, layered/onion/slices/modules, naming/annotation/Spring rules, member rules, custom predicates & conditions, all in Kotlin | `references/rules-cookbook.md` |
| Setting up, configuring or debugging - Gradle, `@AnalyzeClasses` options, caching, `archunit.properties`, freezing legacy violations, ignore patterns, common errors | `references/setup-and-config.md` |

Source: ArchUnit User Guide 1.5.1 (https://www.archunit.org/userguide/html/000_Index.html), API checked against `archunit-1.5.1.jar`.
