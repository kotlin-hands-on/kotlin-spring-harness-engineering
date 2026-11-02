# Power-assert compiler plugin — policy & usage

**Status:** Experimental plugin (bundled with the Kotlin compiler, verified on 2.4.20). **Project policy: enable in every module with tests.**
Rewrites assertion calls so a failure prints every intermediate value — no assertion library needed.

## Enable

Gradle:
```kotlin
plugins { kotlin("plugin.power-assert") version "<kotlin-version>" }

@OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions = listOf(
        "kotlin.assert", "kotlin.require", "kotlin.check",
        "kotlin.test.assertTrue", "kotlin.test.assertFalse", "kotlin.test.assertEquals", "kotlin.test.assertNull", "kotlin.test.assertNotNull",
    )
    // compilationFilter defaults to PowerAssertCompilationFilter.TESTS; use ALL to also transform main-source require/check
}
```

Maven: `<compilerPlugins><plugin>power-assert</plugin></compilerPlugins>` + dependency `kotlin-maven-power-assert`; `<pluginOptions><option>power-assert:function=kotlin.test.assertTrue</option>…`.

**Default transforms only `kotlin.assert`** (which is disabled at runtime without `-ea`!) — always list the `kotlin.test.*` functions you actually use.

Only functions whose **last parameter is a `String` or `() -> String` message** can be transformed (`assertEquals`, `assertTrue`, `assertIs`, `assertNull`, `assertContains`, ...). `assertFailsWith` takes the block last and cannot be listed.

## What you get (verified output)

```kotlin
@Test fun t() {
    val hello = "Hello"; val world = "world!"
    assertTrue(hello.length == world.substring(1, 4).length, "Incorrect length")
}
```
```
org.opentest4j.AssertionFailedError: Incorrect length
assertTrue(hello.length == world.substring(1, 4).length, "Incorrect length")
           |     |      |  |     |               |
           |     |      |  |     |               3
           |     |      |  |     orl
           |     |      |  world!
           |     |      false
           |     5
           Hello
```

Transformable functions: any function whose last parameter is `String`/`() -> String` message (or annotated `@PowerAssert`). Custom ones:

```kotlin
import kotlin.powerassert.PowerAssert

@PowerAssert                                          // no build-file registration needed
fun AssertScope.that(condition: Boolean, @PowerAssert.Ignore message: String? = null) {
    if (!condition) errors += PowerAssert.explanation?.toDefaultMessage() ?: (message ?: "failed")
}
```
`PowerAssert.explanation` (a `CallExplanation`) is non-null only when the call site was transformed; `@PowerAssert.Ignore` keeps the message argument out of the diagram. Soft-assertion scopes collect explanations and throw once.

## Policy

- Tests use plain `kotlin.test` (`assertTrue`, `assertEquals`, `assertNull`) **with power-assert** — no AssertJ/Kotest matcher DSLs just for readable failures.
- **Inline the expression into the assertion.** `assertTrue(isValid)` prints `false`; `assertTrue(person.age in 21..28 && person.name.startsWith("A"))` prints every operand. Don't pre-compute booleans.
- Prefer `assertTrue(a == b)` over `assertEquals(a, b)` when `a`/`b` are chains — the diagram shows the chain, `assertEquals` only shows the two ends.
- Register `kotlin.require`/`kotlin.check` too; precondition failures in tests get diagrams for free.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| Failure message is the plain JUnit one | Function not listed in `functions` (default = only `kotlin.assert`), or plugin not applied to the test compilation |
| `assert(...)` never fails | JVM `assert` needs `-ea`; use `kotlin.test.assertTrue` or `check` instead |
| Diagram shows only `true`/`false` | Condition was computed into a local first — inline it |
| Library `@PowerAssert` helper prints default message | Consumer project has no power-assert plugin → `explanation == null`; always keep a fallback message |
| Gradle warning about experimental API | Add `@OptIn(ExperimentalKotlinGradlePluginApi::class)` on the `powerAssert {}` block |
| Main-source `require` not transformed | `compilationFilter` defaults to tests — set `PowerAssertCompilationFilter.ALL` if wanted (rarely) |
