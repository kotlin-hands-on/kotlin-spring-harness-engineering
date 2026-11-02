# Unused return value checker — policy & usage

**Status:** Experimental (Kotlin 2.3+, verified on 2.4.20). **Project policy: ALWAYS enabled with `-Xreturn-value-checker=check`.**
Catches the classic "called `plus`/`copy`/`map`/`trim()` and dropped the result" bug at compile time. Warnings are errors when `allWarningsAsErrors` is on.

## Enable

| Build | Setting |
|---|---|
| Gradle | `freeCompilerArgs.add("-Xreturn-value-checker=check")` |
| Maven | `<arg>-Xreturn-value-checker=check</arg>` |

| Mode | Reports unused results of… |
|---|---|
| `disable` | nothing (default) |
| `check` | functions in **`@MustUseReturnValues`** scopes — includes most of the Kotlin **stdlib** (`String.uppercase`, `List.map`, `plus`, `copy`…) and your own annotated classes/files |
| `full` | everything compiled in this module is treated as `@MustUseReturnValues` (own unannotated functions too) |

Ignored regardless of mode: `Unit`/`Nothing` returns, `++`/`--`, results that are passed on, used in conditions, returned, or assigned.

## Annotations (`kotlin.*`, in stdlib since 2.3)

```kotlin
@file:MustUseReturnValues            // whole file — note the PLURAL "Values"
package org.example.money

@MustUseReturnValues                 // or a single class
class Money(val cents: Long) {
    fun plus(other: Money): Money = Money(cents + other.cents)

    @IgnorableReturnValue            // fluent/side-effecting API: ignoring is fine
    fun log(): Money = also { println(it) }
}

fun use(m: Money) {
    m.plus(Money(1))                 // WARNING: Unused return value of 'plus'   (verified)
    m.log()                          // fine
    val _ = m.plus(Money(2))         // explicit discard at ONE call site — `val _`, not bare `_ = …`
}
```

Verified in `check` mode: `"abc".uppercase()`, `listOf(1).map { }` and annotated `Money.plus` warn; an unannotated top-level `fun compute(): Int` does **not** (only under `full`).

## Policy

- Enable `check` everywhere. Annotate **your own** immutable/value types (`Money`, `Result`-like sealed hierarchies, builders that return new instances) with `@MustUseReturnValues`, or put `@file:MustUseReturnValues` on files of pure functions. For new modules prefer `full`.
- Mark genuinely fire-and-forget APIs with `@IgnorableReturnValue` (`MutableCollection.add`-style, fluent loggers, `Deferred`-launching helpers). Do it at the declaration, not at every call site.
- Discard with `val _ = f()` **only** with an inline reason; a discard usually means the wrong function is being called (`map` → `forEach`, `plus` → `+=`/reassignment, `copy` → assign).
- Result objects (`sealed interface Result`) MUST be `@MustUseReturnValues` — dropping a `Failure` is exactly the bug this exists for.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| `Unresolved reference 'MustUseReturnValue'` | Singular. The scope annotation is `@MustUseReturnValues`; the opt-out is `@IgnorableReturnValue` |
| `Unresolved reference '_'` on `_ = f()` | Bare underscore assignment isn't a thing — write `val _ = f()` |
| Own functions never warn | You're in `check` mode with no annotation → add `@file:MustUseReturnValues` or switch to `full` |
| `runIf(...) { }` / `also`-style helpers warn in `full` | Helper returns a value nobody needs → make it return `Unit`, or annotate `@IgnorableReturnValue` |
| Java calls | Java methods are not annotated → not reported in `check`; `full` only covers Kotlin sources of the module |
| Suddenly hundreds of warnings on legacy code | Start with `check`, add annotations per package, then `full` |
