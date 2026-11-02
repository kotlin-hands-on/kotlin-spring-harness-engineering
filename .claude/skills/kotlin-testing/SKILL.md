---
name: kotlin-testing
description: Kotlin/JVM testing stack - JUnit 6 + kotlin.test + power-assert. Use when writing, reviewing or fixing Kotlin tests, setting up or upgrading JUnit 6 (Gradle), parameterized/nested/suspend/coroutine/Flow tests, Kotlin fakes and test fixtures. Applies the `tdd` principles in Kotlin; load `mockk` only when a mock is unavoidable, `spring-testing` for Spring Boot tests.
---

# Kotlin testing - JUnit 6 + kotlin.test + power-assert

Prerequisite: the `tdd` skill (`../tdd/SKILL.md`) - its principles (black-box tests, outcome assertions, zero mocking by default) apply unchanged. This skill only adds the Kotlin/JVM tooling and idioms.

Companion skills: `kotlin-engineer` (language policy; `references/power-assert.md` for assertion rendering), `mockk` (only when a mock is truly unavoidable), `spring-testing` (Spring Boot tests).

## Setup check (run first)

1. **JUnit version.** JUnit 6 (Java 17+, Kotlin 2.2+) on the test classpath?
   - Gradle MCP `inspect_dependencies` on `testRuntimeClasspath` (see `using-gradle`) - `junit-jupiter` and `junit-platform-*` must resolve to `6.x`.
   - Missing or 5.x → `references/junit6-setup.md`.
2. **Assertion stack.** `kotlin.test` rendered by **power-assert** (`kotlin("plugin.power-assert")`); every assertion you use must be in its `functions` list in `build.gradle.kts`.
3. **Fixtures already present?** `grep -rl "Fake\|InMemory" src/test` - reuse existing fakes before writing new ones.

## MUST DO

- **`kotlin.test` assertions** (`assertEquals`, `assertIs`, `assertFailsWith`, `assertContentEquals`, `assertNotNull`). All except `assertFailsWith` (block is its last parameter) are listed in power-assert's `functions`; add any new one you use there. Fall back to `org.junit.jupiter.api.Assertions` only for `assertAll`, `assertTimeoutPreemptively`.
- **Backticked behaviour names:** `` fun `rejects registration when email is already taken`() ``; `@DisplayName` only where backticks are not allowed.
- **Sealed results via `assertIs`:** `val r = assertIs<Success>(service.pay(cmd)); assertEquals(Paid, r.order.status)` - the smart cast gives access to the payload.
- **Fixtures per test:** `private val` fields (JUnit creates a fresh instance per test method) plus `private fun aUser(...)` builders with default arguments - not `lateinit` fields mutated in `@BeforeEach`.
- **Deterministic time:** inject `java.time.Clock` (`Clock.fixed(...)`), `Random(seed)`, an id-generator port.
- **Fakes** in `src/test/kotlin/<pkg>/fakes/`, implementing the production interface; expose recordings as read-only `List` (`val sent: List<Mail>`).

## MUST NOT DO

- **Don't widen visibility** (`private` → `internal`/`public`) for a test (see `kotlin-engineer`).
- **No `Thread.sleep`, `Instant.now()`, `UUID.randomUUID()`** in tests or hidden inside the subject.
- **No `companion object` state** shared between tests; no `@TestMethodOrder`.
- **No JUnit 4 / Vintage** in new modules.
- **No `runBlocking`** around plain suspending code - use `@Test suspend fun`.

## SHOULD DO

- **Power-assert:** keep the whole expression inline so the diagram shows intermediate values (`assertTrue(result.items.map { it.id }.contains(id))`).
- **Parameterized:** `@CsvSource` for simple value tables; `@MethodSource` returning `Arguments`/`Pair` for non-trivial types; `@EnumSource` for all enum branches; `@NullAndEmptySource` for input validation.
- **`@Nested` inner classes** group by scenario (`inner class WhenEmailTaken`), not by method.
- **Coroutines:** `@Test suspend fun` for straightforward suspending code; `runTest` + `StandardTestDispatcher` when the subject launches concurrent work or needs virtual time; `turbine` for `Flow`.
- **Lifecycle:** `@BeforeAll` / `@TestInstance(PER_CLASS)` only for starting expensive infrastructure (containers).
- **Files:** `<Subject>Test.kt` in the subject's package; Spring integration tests use the shared `@IntegrationTest` meta-annotation (`spring-testing`), which carries `@Tag("integration")` so they can be filtered.
- **JUnit 6.1 built-ins** (`@DefaultLocale`, `@DefaultTimeZone`, `@SetSystemProperty`, `@RestoreSystemProperties`) instead of hand-rolled `@BeforeEach`/`@AfterEach` juggling.

## References

| Load when | File |
|---|---|
| Writing or reviewing Kotlin tests / fakes - good vs bad tests & mocks in Kotlin | `references/examples.md` |
| Setting up / upgrading the stack - JUnit 6 wiring (Gradle), power-assert config, JUnit 6 facts, stack pitfalls | `references/junit6-setup.md` |
