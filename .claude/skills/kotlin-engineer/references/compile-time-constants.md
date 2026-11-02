# Improved compile-time constants (`-Xintrinsic-const-evaluation`) — policy & usage

**Status:** Experimental (verified on Kotlin 2.4.20). **Project policy: ALWAYS enabled.**
Lets `const val` initializers use stdlib "intrinsic" functions and templates that are evaluated at compile time — no more duplicated magic numbers/strings kept in sync by hand.

## Enable

| Build | Setting |
|---|---|
| Gradle | `freeCompilerArgs.add("-Xintrinsic-const-evaluation")` |
| Maven | `<arg>-Xintrinsic-const-evaluation</arg>` |

Without the flag these all fail with `Const 'val' initializer must be a constant value` (verified).

## What becomes `const`-able (verified)

```kotlin
const val GREETING = "Hello"
const val LEN = GREETING.length                        // → 5
const val UPPER = GREETING.uppercase()                 // → "HELLO"
const val TPL = "$GREETING, world (${GREETING.length})" // templates with const parts
const val CONCAT = GREETING + " " + 42.toString()      // → "Hello 42"
const val MAX_RETRIES = 3
const val TIMEOUT_MS = MAX_RETRIES * 1_000L            // arithmetic on consts
const val API_PREFIX = "/api/v" + 2
```

Rules of thumb for what folds: pure, intrinsic operations on primitives and `String` — arithmetic, comparisons, `toString()`, `length`, `uppercase()/lowercase()`, `plus`, `trim*`, `substring` with const args, string templates whose parts are consts. Anything touching collections, regex, user functions or `Any` receivers is **not** folded.

Since the values are true constants they are inlined at use sites, usable in annotation arguments and `when` branches, and visible to Java as static finals.

## Policy

- Derive constants from other constants (`const val TIMEOUT_MS = RETRIES * BASE_MS`) instead of hardcoding the product and a comment.
- Build paths/prefixes/keys from a single `const val` root: `const val USERS = "$API_PREFIX/users"`.
- Anything that *can* be `const val` (top-level or in `object`/`companion`) **should** be — `val` for primitives/strings in `object`s is a smell.
- Don't try to be clever: if it doesn't fold, make it a plain `val` rather than restructuring code around the evaluator.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| `Const 'val' initializer must be a constant value` | Flag missing, compiler too old, or the expression uses a non-intrinsic call — verify each part is itself const |
| Same error on `"…".trimIndent()`/`format` | Not all `String` members are intrinsic; keep it to length/case/concat/substring, otherwise use `val` |
| `const val` inside a class body | Still not allowed — `const` only at top level, in `object`, or in `companion object` |
| Binary-incompat surprises in libraries | Consts are **inlined into consumers**; changing the value needs consumers to recompile — same as before, now just for more expressions |
| IDE shows an error the compiler doesn't | IDE evaluator lags; trust the build |
