# Contracts (incl. extended contracts) — policy & usage

**Status:** `kotlin.contracts` base API Experimental since 1.3 (`@OptIn(ExperimentalContracts::class)`); the 2.3/2.4 **extended contracts** (`holdsIn`, contracts on property getters/operators/more functions, `condition implies returns`) need `@OptIn(ExperimentalExtendedContracts::class)`. Verified on 2.4.20.

## Enable

No compiler flag is needed on Kotlin ≥ 2.4 — the former `-Xallow-contracts-on-more-functions`, `-Xallow-condition-implies-returns-contracts`, `-Xallow-holdsin-contract` are reported as **redundant** (verified). Only opt-ins:

```kotlin
@OptIn(ExperimentalContracts::class, ExperimentalExtendedContracts::class)
```
Gradle project-wide: `kotlin { compilerOptions { optIn.addAll("kotlin.contracts.ExperimentalContracts", "kotlin.contracts.ExperimentalExtendedContracts") } }`.

On Kotlin 2.3.x the extended kinds still need the `-Xallow-*` flags above.

## Contract kinds (all verified)

```kotlin
import kotlin.contracts.*

// 1. returns(value) implies condition — smart-cast the receiver/argument after the call
fun String?.isValid(): Boolean {
    contract { returns(true) implies (this@isValid != null) }
    return this != null && isNotBlank()
}
if (s.isValid()) s.length                         // s smart-cast to String

// 2. Contracts on PROPERTY getters (extended)
val String?.hasText: Boolean
    get() {
        contract { returns(true) implies (this@hasText != null) }
        return !isNullOrBlank()
    }
if (s.hasText) println(s.length)

// 3. returnsNotNull
fun <T : Any> T?.orFail(msg: String): T {
    contract { returnsNotNull() }
    return this ?: error(msg)
}

// 4. returns() implies cond  — require/check-style guards; combine with callsInPlace
fun requireState(cond: Boolean, lazyMsg: () -> String) {
    contract {
        returns() implies cond                       // reaching the next line proves `cond`
        callsInPlace(lazyMsg, InvocationKind.AT_MOST_ONCE)
    }
    if (!cond) throw IllegalStateException(lazyMsg())
}
requireState(v != null) { "v" }; v + 1              // v: Int

// 5. holdsIn (extended): condition is known true INSIDE the lambda
inline fun <T> runIf(cond: Boolean, block: () -> T): T? {
    contract { cond holdsIn block }
    return if (cond) block() else null
}
runIf(n != null) { n + 1 }                          // n smart-cast inside block

// 6. callsInPlace — allow `val` initialisation / definite assignment through a lambda
inline fun <R> once(block: () -> R): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return block()
}
val x: Int
once { x = 42 }                                     // compiles: EXACTLY_ONCE
```

`InvocationKind`: `EXACTLY_ONCE`, `AT_MOST_ONCE`, `AT_LEAST_ONCE`, `UNKNOWN`.

## Policy

- Every custom **guard** (`requireX`, `checkX`, `assertValid`, `failIfNull`) and every `isXxx()` **predicate on nullable receivers** gets a contract — it's what makes the null-safety rule (`no !!`) practical.
- Every **inline higher-order helper** (`transaction { }`, `measure { }`, `withLock { }`, `once { }`) declares `callsInPlace` so callers can initialise `val`s and get definite-return analysis.
- Use `holdsIn` for conditional-execution helpers so callers don't re-check what they just checked.
- Contracts are **trusted, not verified**: the body MUST actually honour the contract. A wrong `returns(true) implies (x != null)` turns into an NPE at the call site. Review contracts like `!!`.
- Contract must be the **first statement** of the function body; the function must have a block body.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| `This declaration needs opt-in … ExperimentalExtendedContracts` | `holdsIn`, property-getter contracts etc. → add the second opt-in |
| `Contract description … not first statement` | Move `contract { }` above everything, incl. logging |
| Contract on an `open`/`abstract`/override member is rejected | Contracts only on final functions/getters; overriding could break the promise |
| `-Xallow-*-contract` warnings "redundant for language version 2.4" | Remove the flags; they're only for 2.3 |
| Smart cast doesn't happen after the call | Compare exactly the argument expression: `implies (x != null)` on a parameter `x`, `this@fn != null` on receivers; lambda-captured `var`s never smart-cast |
| `callsInPlace` on a non-inline function | Allowed but you must call the lambda exactly as declared; the compiler doesn't check — keep them `inline` |
