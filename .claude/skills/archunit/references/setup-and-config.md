# ArchUnit setup, configuration & troubleshooting (1.5.1)

## Artifacts

| Artifact | Use |
|---|---|
| `com.tngtech.archunit:archunit-junit6` | **JUnit 6** - aggregate of `archunit-junit6-api` (annotations) + `archunit-junit6-engine` (the `archunit` TestEngine) |
| `com.tngtech.archunit:archunit-junit5` | JUnit 5 projects only |
| `com.tngtech.archunit:archunit` | Core only - plain `ClassFileImporter` + `rule.check(classes)` from any framework (no caching, no `@ArchTest`) |

### Gradle (this repo: Spring Boot BOM, JUnit 6.1)

ArchUnit is not managed by the Spring Boot BOM, so pin the version:

```kotlin
dependencies {
	testImplementation("com.tngtech.archunit:archunit-junit6:1.5.1")
}

tasks.withType<Test> {
	useJUnitPlatform()            // picks up the archunit engine next to jupiter
}
```

With a version catalog: `archunit = "1.5.1"` and `archunit-junit6 = { module = "com.tngtech.archunit:archunit-junit6", version.ref = "archunit" }`.

Verify with these checks:

- Gradle MCP `inspect_dependencies` on `testRuntimeClasspath`: the engine's `junit-platform-engine` must line up with the Jupiter 6.x platform.
- Gradle MCP `gradle` with `test --tests '*ArchitectureTest'`, results via `query_build` (`kind=TESTS`): rules appear as tests named after their fields.

ArchUnit reads **compiled classes**, so the `test` task already depends on `compileKotlin`. Nothing extra to wire.

## `@AnalyzeClasses` options

| Attribute | Effect |
|---|---|
| *(none)* | imports the package of the test class |
| `packagesOf = [App::class]` | packages of the given classes (refactor-safe, **preferred**) |
| `packages = ["com.example"]` | packages by name |
| `classes = [Foo::class]` | exactly these classes (combinable with the above) |
| `locations = [MyLocationProvider::class]` | custom `LocationProvider` returning `Set<Location>` |
| `wholeClasspath = true` | entire classpath (slow - avoid) |
| `importOptions = [...]` | filters: `ImportOption.DoNotIncludeTests`, `DoNotIncludeJars`, `DoNotIncludeArchives`, `OnlyIncludeTests`, or your own `ImportOption` |
| `cacheMode = CacheMode.PER_CLASS` | drop the import after this class (default `FOREVER` caches by location, soft references) |

Use a meta-annotation to avoid repeating the configuration:

```kotlin
@Retention(AnnotationRetention.RUNTIME)
@AnalyzeClasses(packagesOf = [HarnessEngineeringSpringApplication::class], importOptions = [ImportOption.DoNotIncludeTests::class])
annotation class AnalyzeMainClasses
```

### Other JUnit integration features

- `@ArchTest` on `val` fields (`ArchRule` or `ArchTests`) or on `fun name(classes: JavaClasses)`.
- ``ArchTests.`in`(SomeRules::class.java)`` includes every `@ArchTest` of another class, evaluated against **this** class's import. Use it for rule libraries shared across modules.
- `@ArchIgnore(reason = "...")` skips a rule. **JUnit's `@Disabled` is ignored by the ArchUnit engine.**
- `@ArchTag("slow")` / `@ArchTags` add JUnit tags that Gradle can filter (`useJUnitPlatform { includeTags(...) }`).
- Run a single rule with `-Darchunit.junit.testFilter=layers_are_respected` (comma-separated).

## `archunit.properties`

Put the file at `src/test/resources/archunit.properties`, the classpath root. Any key can be overridden from the command line with `-Darchunit.<key>=<value>`, e.g. as a Gradle `systemProperty` on the `test` task.

```properties
# test names: layers_are_respected -> "layers are respected"
junit.displayName.replaceUnderscoresBySpaces=true

# default true: a should-clause over zero classes fails (catches rules broken by renames)
archRule.failOnEmptyShould=true

# freezing (see below)
freeze.store.default.path=src/test/resources/archunit_store
freeze.store.default.allowStoreCreation=false
freeze.store.default.allowStoreUpdate=true
freeze.refreeze=false

# cycle detection limits
cycles.maxNumberToDetect=100
cycles.maxNumberOfDependenciesPerEdge=20

# missing classes: false = stub instead of looking them up on the classpath (faster, less info)
resolveMissingDependenciesFromClassPath=true
```

Further options:

- **Partial resolution**: `classResolver=com.tngtech.archunit.core.importer.resolvers.SelectedClassResolverFromClasspath` with `classResolver.args=com.example` resolves only your packages and stubs the rest.
- **Shorter messages**: `failureDisplayFormat=<FQN of a FailureDisplayFormat>`, e.g. to shorten FQNs in messages.

## Introducing rules into existing code

Three options, from best to worst:

1. **Fix the violations** in the same change, if there are only a few.
2. **Freeze** the rule: existing violations are recorded, and only **new** ones fail.

   ```kotlin
   @ArchTest
   val layers_are_respected: ArchRule = FreezingArchRule.freeze(
       layeredArchitecture() /* ... */,
   )
   ```

   - The first run writes the store, but only if `freeze.store.default.allowStoreCreation=true`. Set it in `archunit.properties`, run the tests once, then set it back to `false` (a `-D` flag on the Gradle command line reaches the Gradle JVM, not the test JVM). The store is a set of text files.
   - **Commit the store** (`src/test/resources/archunit_store/`). Keep `allowStoreCreation=false` in the committed config so CI never creates a new store silently.
   - Fixed violations are removed from the store automatically, so the frozen set only shrinks. Line numbers are ignored by default (the `ViolationLineMatcher`), so moving code doesn't count as a new violation.
   - **Refreezing**: `freeze.refreeze=true` accepts all current violations. Use it only after a deliberate decision, never to get a build green.
   - Custom storage: `FreezingArchRule.freeze(rule).persistIn(store)` / `associateViolationLinesVia(matcher)`, or `freeze.store=<FQN>` / `freeze.lineMatcher=<FQN>`.
3. **Ignore patterns**, as a last resort: `src/test/resources/archunit_ignore_patterns.txt` holds one regex per line (`#` starts a comment), and violations whose message matches are dropped. This hides violations across *all* rules, so prefer freezing or `ignoreDependency(...)` on the specific rule.

## Common errors

| Symptom | Cause | Fix |
|---|---|---|
| `Rule '...' failed to check any classes. This means either that no classes have been passed to the rule at all, or that no classes passed to the rule matched the that() clause.` | Should-clause got zero classes: wrong pattern, package renamed, or the package/annotation/field kind doesn't exist yet (also hits library rules and `slices()` in a fresh project) | Fix the pattern, or add the rule together with the first code it guards. For planned layers use `optionalLayer`/`withOptionalLayers(true)`; use `allowEmptyShould(true)` only with a comment explaining why |
| `Architecture Violation ... Layer 'X' is empty` | Layered architecture with a layer that has no classes | `optionalLayer("X")` or `withOptionalLayers(true)` |
| `StoreInitializationFailedException: Creating new violation store is disabled` | Frozen rule, no store yet, `allowStoreCreation=false` (the default) | Create the store once locally with `-Darchunit.freeze.store.default.allowStoreCreation=true` and commit it |
| Architecture test class not executed / 0 tests | `archunit-junit6` missing, or the engine is excluded via `includeEngines` | Add the dependency, or add `"archunit"` to the engines |
| `@Disabled` rule still runs | ArchUnit engine ignores `@Disabled` | `@ArchIgnore(reason = ...)` |
| Rule passes although code violates it | Pattern matches class name instead of package (`..Foo`); `accessClassesThat` used where `dependOnClassesThat` was meant; test classes imported by mistake | Use name predicates; use `dependOnClassesThat`; add `DoNotIncludeTests` |
| Violations in `FooKt`, `Foo$Companion`, `$WhenMappings`, lambdas | Kotlin-generated classes | `areTopLevelClasses()`, `areNotAnonymousClasses()`, exclude `.*Kt` |
| Whitelist rule flags `kotlin.jvm.internal.Intrinsics` / `java.lang.Object` | Every Kotlin class depends on the stdlib | Add `"kotlin.."`, `"java.."` to `resideInAnyPackage(...)` |
| `internal` boundary not enforced | `internal` is `public` in bytecode | Enforce via packages/layers or separate Gradle modules |
| Test classes show up as violators | Import includes `build/classes/kotlin/test` | `importOptions = [ImportOption.DoNotIncludeTests::class]` |
| Slow / high heap | Huge import (`wholeClasspath`, JARs), many cycles | Narrow `packagesOf`, `DoNotIncludeJars`, `cycles.maxNumberToDetect`, `resolveMissingDependenciesFromClassPath=false` |
| Frozen rule fails on first CI run | Store not committed / CI not allowed to create it | Commit `archunit_store/`; create it locally once |
| `ArchTests.in(...)` doesn't compile | `in` is a Kotlin keyword | ``ArchTests.`in`(Rules::class.java)`` |
