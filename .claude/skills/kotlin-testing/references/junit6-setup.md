# Kotlin test stack - JUnit 6 + kotlin.test + power-assert

Baseline JUnit knowledge is assumed. This file covers build wiring, JUnit 6 specifics and stack pitfalls. Conventions live in `../SKILL.md`; with Spring Boot dependency management see `spring-testing` for the BOM version override.

## JUnit 6 wiring

JUnit 6 = Jupiter/Platform/Vintage share one version (`6.x`). Baseline: **Java 17+, Kotlin 2.2+**.

Gradle with a version catalog:

```toml
[versions]
junit = "6.1.3"          # one version for platform + jupiter
[libraries]
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test" }
```

```kotlin
dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)                                       // picks the junit5 variant automatically
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") // needed for `@Test suspend fun`
    testRuntimeOnly(libs.junit.platform.launcher)                              // required since Gradle 9
}
tasks.withType<Test> { useJUnitPlatform() }
```

Power-assert (Gradle):

```kotlin
@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf("kotlin.test.assertEquals", "kotlin.test.assertNotEquals", "kotlin.test.assertTrue", "kotlin.test.assertFalse", "kotlin.test.assertNull", "kotlin.test.assertNotNull", "kotlin.test.assertIs", "kotlin.test.assertContains", "kotlin.test.assertContentEquals")
}
```

## JUnit 6 facts that matter for Kotlin

- `@Test suspend fun` works natively — **no** `runBlocking`/`runTest` wrapper for plain suspending code (needs `kotlinx-coroutines-core` on the test classpath). Still use `kotlinx-coroutines-test` `runTest` when you need virtual time / `TestDispatcher`.
- All APIs carry JSpecify nullability → `Optional<T>`/platform types are gone; `assertNotNull(x)` from `kotlin.test` smart-casts.
- `@Nested` classes run in a deterministic order and inherit `@TestMethodOrder`. `MethodOrderer.Alphanumeric` is removed → don't order tests at all.
- `@CsvSource` uses FastCSV: strict quoting, `lineSeparator` removed, `commentCharacter` available. Malformed CSV now *fails* discovery.
- `ExtensionContext.Store.getOrComputeIfAbsent` → `computeIfAbsent`. `@EnabledOnJre(JAVA_8..16)` constants are gone.
- Built-ins since 6.1: `@DefaultLocale`, `@DefaultTimeZone`, `@SetSystemProperty`/`@ClearSystemProperty`/`@RestoreSystemProperties` — use these instead of hand-rolled `@BeforeEach/@AfterEach` juggling.
- Vintage (JUnit 4) is deprecated and logs a discovery warning — never add it to a new module.

## Suspend + Flow shape

```kotlin
@Test
suspend fun `emits each price update once`() = ticker.prices("ACME").test {
    assertEquals(Price(10), awaitItem())
    assertEquals(Price(11), awaitItem())
    cancelAndIgnoreRemainingEvents()
}
```

## Stack pitfalls (symptom → fix)

| Symptom | Cause / fix |
|---|---|
| `No tests found` / tests silently skipped on Gradle 9 | Missing `junit-platform-launcher` on `testRuntimeOnly` or no `useJUnitPlatform()` |
| `Unsupported class file major version` / `UnsupportedClassVersionError` | JUnit 6 needs JDK 17+ at test runtime — fix `jvmToolchain` |
| Kotlin `suspend` test "not a test" / fails to run | Older Jupiter (5.x) on the classpath, or `kotlinx-coroutines-core` missing — verify `junit-jupiter` resolves to 6.x |
| `@CsvSource` fails with `CsvParseException` after upgrade | FastCSV strictness: quote values containing `,`/`'`, drop `lineSeparator`, use `textBlock` |
| Power-assert prints plain message | Function not in `functions` list or plugin missing on the test compilation |
| `assertEquals` on `data class` with `Instant.now()` differs by ms | Inject `Clock`; never compare wall-clock values |
| Coroutine test hangs | Un-completed `Flow`/`Channel` under `runTest`; use `cancelAndIgnoreRemainingEvents()` / `advanceUntilIdle()` or `backgroundScope` |
