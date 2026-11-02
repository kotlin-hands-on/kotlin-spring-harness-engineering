# Name-based destructuring (complete mode) — policy & usage

**Status:** Experimental (Kotlin 2.3+, verified on 2.4.20). **Project policy: ALWAYS enabled, always in `complete` mode.**
Positional `val (a, b) = obj` is a silent-bug generator (swapped fields after a reorder compile fine); name-based makes destructuring safe by construction.

## Enable

| Build | Setting |
|---|---|
| Gradle | `freeCompilerArgs.add("-Xname-based-destructuring=complete")` |
| Maven | `<arg>-Xname-based-destructuring=complete</arg>` |

Modes (only `complete` is policy; the others exist for migration):

| Mode | `val (a, b) = x` means | `[a, b]` syntax | Full form `(val a = p) = x` |
|---|---|---|---|
| `only-syntax` | positional (old) | yes | yes |
| `name-mismatch` | positional, **warns** when names ≠ data-class property names | yes | yes |
| `complete` | **name-based** | yes (only way to do positional) | yes |

## Syntax

```kotlin
data class User(val username: String, val email: String, val age: Int)
val user = User("alice", "alice@example.com", 30)

val (email, username) = user          // short form: matched BY NAME → email="alice@…", username="alice" (order irrelevant)
val (age) = user                      // pick any subset, any order
(val name = username, val mail = email) = user   // full form: rename; `val`/`var` INSIDE the parens, nothing in front
(val name = username, val age) = user            // mix rename + shorthand (`val age` == `val age = age`)
val [first, second] = "a" to "b"      // POSITIONAL (componentN) → square brackets
for ((key, value) in map) { }         // Map.Entry has `key`/`value` → works by name
for ([k, v] in map) { }               // positional alternative
users.forEach { (username, email) -> } // lambdas: same rules
```

Rules:
- Works on **any type with matching properties** (member or extension properties, interfaces too) — not just data classes. Desugars to `val tmp = e; val x = tmp.x; …`, accessed in declaration order.
- Names must resolve to a property of the receiver; otherwise compile error `Unresolved reference 'a2' on receiver of type 'Point'` (verified).
- `Pair`/`Triple`: `(first, second)`/`(first, second, third)` work by name; anything else needs `[a, b]`.
- `_` only in positional `[_, b]` or with rename in full form `(val _ = hidden)`; plain `(_ , b)` is not a name.
- Type annotations go in the full form: `(val id: Long = userId, val name)`.
- Modifiers/annotations can't precede the inner `val`/`var`.

## Policy

- **Never write `val (a, b) = someList`/`Pair` with invented names** — in `complete` mode it doesn't compile; use `[a, b]` or (better) meaningful properties.
- Prefer name-based over `[]`. Use `[]` only for genuinely ordered tuples (`Pair`, `Triple`, `List`, `IndexedValue`).
- When naming collides with an outer variable, use the full form to rename instead of shadowing.
- Rename-on-destructure replaces `val x = obj.longPropertyName` noise: `(val x = longPropertyName) = obj`.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| `Unresolved reference 'a' on receiver of type …` | Positional intent under `complete` → use `[a, b]` or real property names |
| `Initializer required for destructuring declaration` / `Expecting ')'` | Wrote `val (val a = p) = x` — drop the leading `val`; full form has no keyword in front |
| Existing code silently changes meaning after enabling | Only if variable names ≠ property names. Run one build with `name-mismatch` first on legacy code, fix warnings, then switch to `complete` |
| Getter side effects in odd order | Properties are read in the destructuring order, not class order |
| IDE shows red but build passes | IDE Kotlin plugin lags the compiler; K2 mode + matching Kotlin version required |
