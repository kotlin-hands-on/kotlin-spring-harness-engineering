# Collection literals — policy & usage

**Status:** Experimental (Kotlin 2.3+, verified on 2.4.20). **Project policy: ALWAYS enabled; prefer `[…]` over `listOf/setOf/mutableListOf(…)`.**

## Enable

| Build | Setting |
|---|---|
| Gradle | `freeCompilerArgs.add("-Xcollection-literals")` |
| Maven | `<arg>-Xcollection-literals</arg>` |

Requires Kotlin ≥ 2.3 — older compilers fail with `Array literals outside of annotations are unsupported`.

## Semantics (all verified)

```kotlin
val inferred = [1, 2, 3]                    // no expected type → List<Int>
val empty: List<String> = []                // empty literal needs an expected type
val set: Set<String> = ["a", "b"]           // expected type drives resolution
val dedup: Set<Int> = [1, 1, 2]             // → [1, 2]
val ml: MutableList<Int> = [1, 2]
val arr: Array<Int> = [1, 2]
val ia: IntArray = [1, 2]                   // primitive arrays too
val nested: List<List<Int>> = [[1], [2, 3]]
val mixed = [1, "a"]                        // List<Comparable<*> & Serializable> — avoid; be explicit
fun ids(): List<Long> = [1, 2]              // return position = expected type
render(items = ["x", "y"])                  // argument position = parameter type
@Suppress(["a", "b"])                       // annotations: array literal as before
```

Resolution rule: the literal `[a, b]` is a call to `T.Companion.of(a, b)` on the **expected type `T`** (default `List`). Any type can opt in:

```kotlin
class Bag<T>(val items: List<T>) {
    companion object {
        operator fun <T> of(vararg items: T): Bag<T> = Bag(items.toList())
    }
}
val bag: Bag<String> = ["a", "b"]           // → Bag[a, b]
```

## Policy

- Use `[…]` for `List`, `Set`, `Mutable*`, arrays and any own value-collection type with an `operator fun of`. Keep `listOf()` only where a literal would need an ugly type ascription that already exists elsewhere.
- Always give an **expected type** for `[]` and for anything other than `List` (declaration type, return type, or parameter type) — never rely on inference for `Set`/arrays.
- Don't create heterogeneous literals (`[1, "a"]`); the inferred intersection type is never what you want.
- Domain collection wrappers (`OrderLines`, `Tags`) get a `companion object { operator fun of(vararg …) }` so call sites read `val tags: Tags = ["a", "b"]`.
- Map literals don't exist yet — keep `mapOf(k to v)`.

## Pitfalls

| Symptom | Cause / fix |
|---|---|
| `Array literals outside of annotations are unsupported` | Flag missing **or** compiler < 2.3 (bump the Kotlin Gradle plugin) |
| `Initializer type mismatch: expected 'List<Int>', actual 'Array<Int>'` | Same root cause — without the flag `[…]` is an array literal |
| `[]` → "Not enough information to infer type" | Give an expected type |
| Custom type not accepted | `of` must be an `operator fun` on the **companion**, taking `vararg` |
| `Set` literal drops elements | Intentional — literal delegates to `Set.of`, duplicates collapse |
| Confusing `[a, b]` destructuring vs literal | `val [a, b] = pair` (left side) is positional destructuring; `= [a, b]` (right side) is a literal. Both exist under the project flags |
