---
name: kotlin-engineer
description: Kotlin 2.x project policy and experimental features. Use when writing, reviewing, or refactoring Kotlin code - null-safety (`!!`, `requireNotNull`), sealed results instead of exceptions for domain errors, value classes, immutability, minimum visibility, and the mandatory experimental compiler features - collection literals `[1, 2]`, name-based destructuring, intrinsic const evaluation, the return-value checker - plus contracts and power-assert.
---

# Kotlin - policy and pitfalls

Baseline Kotlin knowledge (data/sealed/value classes, scope functions, null-safety operators, extension functions, `when` exhaustiveness) is assumed. This skill does not teach the language - it encodes the project policy.

## MUST DO

- **Null-safety via `?`, `?.`, `?:`, `let`, `requireNotNull`, `checkNotNull`.** Prefer to never use `!!`, if used document why on the same line.
  * `requireNotNull` & `checkNotNull` cover invariants of functions, or classes (when used in `init` block)
- **Prefer Sealed hierarchies and algebraic Data types** for closed result / state types - the compiler then forces every caller to handle every outcome, which exceptions don't (`sealed interface Result { data class Success(...); data class Failure(...) }`) + exhaustive `when` without `else`.
  * Prefer Sealed hierarchies and algebraic Data types, use result objects to cover domain-logic errors instead of throwing Exceptions
  * Prefer throwing Exceptions for non-domain logic errors. (e.g. DB connection failure)
- **Value classes (`@JvmInline value class`) for domain identifiers and Value Objects (`UserId`, `Email`)** - enforce type safety without overhead
- **`data class` only for pure value types.** Not for entities, services, or anything with behaviour or lifecycle
- **Immutability by default** - `val` over `var`, `List` over `MutableList` in public API, `copy()` on data classes instead of mutation
- **Use Named arguments for 3+ parameters** - prevents silent arguments swaps at call sites.
- **Minimum visibility of classes, properties & functions** - `private` wherever possible; `public` only for what is part of the API. Less surface means fewer accidental couplings and fewer things tests can latch onto.
- **Always enable AND use these (experimental) compiler features** in every project (`kotlin { compilerOptions { freeCompilerArgs.addAll(...) } }` in `build.gradle.kts`):
  * `-Xname-based-destructuring=complete` - destructure by property name `val (email, username) = user`; positional only via `val [a, b] = pair` → `references/name-based-destructuring.md`
  * `-Xcollection-literals` - `val ids: Set<Long> = [1, 2]` instead of `setOf(...)`; give own collection types an `operator fun of` → `references/collection-literals.md`
  * `-Xintrinsic-const-evaluation` - derive `const val`s from other consts (`"$PREFIX/users"`, `RETRIES * BASE_MS`) → `references/compile-time-constants.md`
  * `-Xreturn-value-checker=check` - annotate value/result types with `@MustUseReturnValues`, never drop a result silently → `references/return-value-checker.md`
  * If a project lacks a flag, add it (and fix the fallout) as part of the change - don't write positional/`listOf` code to dodge it. This is a policy-mandated change, not scope creep (`change-review` treats it that way).

## MUST NOT DO

- **No `!!`** without a commented reason. Refactor to `?.` / `requireNotNull(x) { "why" }` / `checkNotNull(x) { "why" }`
- **No platform-type leaks (`String!`)** in public API — annotate Java interop returns with `@NotNull` / `@Nullable` on the Java side, or cast explicitly.
- **No catching `Throwable`** — you'll catch `OutOfMemoryError`, `StackOverflowError`, and cancellation. Use `Exception` and rethrow cancellation.
- **No `internal` in application modules** - it only restricts access across Gradle modules, so in a single application module it adds noise without protection. Visibility here is `private` where possible, otherwise `public`; `internal` is for library modules.
- **Don't change visibility to public / internal for easier unit tests**


## SHOULD DO

- **Prefer early returns over deeply nested code** - either through `?: return ...` or `if(x) return ...` instead of nested `if` / `else` / `elseif` statements
- **Use guard clauses in `when` over nested `if` in `when` case** - guard clauses are more expressive & prevent nesting `when (x) { is Success if x.result != null -> ... }`
- **Functions should be scoped to do one thing** - enhances composition and readability
- **Public API of functions & classes should be documented using KDoc**

## SHOULD NOT DO

- **No useless indirection** - no interfaces / abstract classes for a single implementation; extract one when a second implementation appears. **Exception: ports** to things outside the process (DB, mail, payment, third-party HTTP, clock, id generation) get an interface even with one production implementation - that is what lets tests use a fake instead of a mock (`tdd`).


## References

| Load when | File |
|---|---|
| Idioms - scope functions, value/data/sealed classes, extension function, inline/reified, delegates, `Result<T>` | `references/idioms.md` |
| Name-based destructuring (`-Xname-based-destructuring=complete`) - `val (a, b) = obj` by name, `[a, b]` positional, full form `(val x = prop) = obj`, migration & errors | references/name-based-destructuring.md |
| Collection literals (`-Xcollection-literals`) - `[1, 2]` for List/Set/Mutable*/arrays, expected-type resolution, `operator fun of` for own types | references/collection-literals.md |
| Compile-time constants (`-Xintrinsic-const-evaluation`) - `const val` from templates/`length`/`uppercase()`/arithmetic, what folds and what doesn't | references/compile-time-constants.md |
| Unused return value checker (`-Xreturn-value-checker=check\|full`) - `@MustUseReturnValues`, `@IgnorableReturnValue`, `val _ = f()` | references/return-value-checker.md |
| Power-assert - plugin setup (Gradle/Maven), `functions` list, `@PowerAssert` for own assertion helpers, writing assertions that render well | references/power-assert.md |
| Contracts & extended contracts - `returns() implies`, `returnsNotNull`, `callsInPlace`, `holdsIn`, contracts on property getters, `ExperimentalExtendedContracts` opt-in, custom guards/predicates | references/contracts.md |

Testing is covered by **separate skills**: `tdd` (`../tdd/SKILL.md`) - TDD loop, black-box behaviour tests, fakes over mocks; `kotlin-testing` (`../kotlin-testing/SKILL.md`) - JUnit 6 + kotlin.test + power-assert; `spring-testing` (`../spring-testing/SKILL.md`) - Spring Boot tests; `archunit` (`../archunit/SKILL.md`) - architecture rules as tests; `mockk` (`../mockk/SKILL.md`) only when a mock is unavoidable.


## Reviewing

When reviewing code: call out MUST-DO / MUST-NOT violations explicitly by rule name and suggest the minimal fix.
