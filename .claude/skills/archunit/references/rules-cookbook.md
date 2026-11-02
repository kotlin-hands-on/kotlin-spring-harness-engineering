# ArchUnit rules cookbook (Kotlin, ArchUnit 1.5)

All snippets are `@ArchTest val ...: ArchRule` fields inside an `@AnalyzeClasses` class (see `../SKILL.md`). They were compiled and run against `archunit-junit6:1.5.1` on Kotlin 2.4 / JUnit 6.1. Imports used throughout:

```kotlin
import com.tngtech.archunit.base.DescribedPredicate
import com.tngtech.archunit.base.DescribedPredicate.alwaysTrue
import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage
import com.tngtech.archunit.core.domain.JavaClasses
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchIgnore
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.junit.ArchTests
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import com.tngtech.archunit.library.Architectures.layeredArchitecture
import com.tngtech.archunit.library.Architectures.onionArchitecture
import com.tngtech.archunit.library.DependencyRules
import com.tngtech.archunit.library.GeneralCodingRules
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices
import com.tngtech.archunit.library.freeze.FreezingArchRule
// Spring annotations as needed: org.springframework.web.bind.annotation.RestController,
// org.springframework.stereotype.Service, org.springframework.boot.context.properties.ConfigurationProperties, ...
```

## Rule anatomy

Every rule has the form **`classes that <PREDICATE> should <CONDITION>`**:

- Entry points (`ArchRuleDefinition`): `classes()`, `noClasses()`, `methods()`, `fields()`, `constructors()`, `codeUnits()`, `members()` and their `no...()` negations. Use `priority(Priority.HIGH).classes()` for a non-default priority.
- Combine predicates with `.and()` / `.or()`, and conditions with `.andShould()` / `.orShould()`.
- Finish with `.because("why")`, or replace the whole generated text with `.as("short rule text")`.
- The failure message is `Architecture Violation [Priority: MEDIUM] - Rule '<text>' was violated (n times):` followed by one line per violation, with `(File.kt:line)`.

## Package identifiers

Patterns match a class's **package name** (`com.example.web` for `com.example.web.UserController`).

| Pattern | Matches |
|---|---|
| `com.example.web` | exactly that package |
| `com.example.*` | exactly one segment below `com.example` |
| `com.example..` | `com.example` and every subpackage |
| `..service..` | any package containing a `service` segment |
| `..service` | packages ending in `service` |
| `com.example.(*)..` | every package below `com.example`; captures the first segment (slice/module name) |
| `com.example.(**)` | every subpackage; captures all segments below `com.example` |
| `..[web|api]..` | either alternative (alternation only inside `[]` or `()`) |

`(..)` is invalid, so use `(**)`. To match classes rather than packages, use `haveSimpleName`, `haveSimpleNameEndingWith` or `haveNameMatching`.

## Dependency direction

```kotlin
@ArchTest
val domain_is_framework_free: ArchRule = noClasses()
    .that().resideInAPackage("..domain..")
    .should().dependOnClassesThat().resideInAnyPackage(
        "org.springframework..", "jakarta.persistence..", "tools.jackson..", "..web..", "..persistence..",
    )
    .because("the domain must be testable and reusable without frameworks or adapters")

// whitelist variant - remember kotlin.. and java..
@ArchTest
val domain_only_uses_std_lib: ArchRule = classes()
    .that().resideInAPackage("..domain..")
    .should().onlyDependOnClassesThat().resideInAnyPackage("..domain..", "kotlin..", "java..", "org.jetbrains.annotations..")

// who may use a package
@ArchTest
val persistence_only_used_by_services: ArchRule = classes()
    .that().resideInAPackage("..persistence..")
    .should().onlyHaveDependentClassesThat().resideInAnyPackage("..persistence..", "..service..", "..config..")
```

- `dependOnClassesThat` covers every dependency: calls, field types, signatures, annotations, inheritance.
- `accessClassesThat` covers only field accesses and method or constructor calls.

## Layered architecture

```kotlin
@ArchTest
val layers_are_respected: ArchRule = layeredArchitecture()
    .consideringOnlyDependenciesInLayers()
    .layer("Web").definedBy("..web..")
    .layer("Service").definedBy("..service..")
    .layer("Persistence").definedBy("..persistence..")
    .optionalLayer("Config").definedBy("..config..")
    .whereLayer("Web").mayNotBeAccessedByAnyLayer()
    .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Config")
    .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service", "Config")
    .ensureAllClassesAreContainedInArchitectureIgnoring("com.example.harnessengineeringspring")
    .because("dependencies point inwards: web -> service -> persistence")
```

- **Scope**:
  - `consideringAllDependencies()` also counts deps to `java..`/`kotlin..`, which matters only for `mayOnlyAccessLayers`.
  - `consideringOnlyDependenciesInLayers()` is the usual choice.
  - `consideringOnlyDependenciesInAnyPackage("com.example..")` limits the rule to your own code.
- **Direction**: there are access-based rules (`mayNotBeAccessedByAnyLayer`, `mayOnlyBeAccessedByLayers`) and outgoing rules (`mayNotAccessAnyLayer`, `mayOnlyAccessLayers`).
- **Empty layers fail**. Use `optionalLayer(...)` or `withOptionalLayers(true)` for layers that don't exist yet.
- **Exceptions**: `ignoreDependency(Origin::class.java, Target::class.java)` (or the String / predicate overloads). Each one needs a reason in a comment.
- **Strays**: `ensureAllClassesAreContainedInArchitecture()` fails for classes outside every layer. The `...Ignoring(packages)` variant exempts e.g. the root package holding `@SpringBootApplication`.

## Onion / hexagonal (ports & adapters)

```kotlin
@ArchTest
val hexagonal_architecture: ArchRule = onionArchitecture()
    .domainModels("..domain.model..")
    .domainServices("..domain.service..")
    .applicationServices("..application..")
    .adapter("rest", "..adapter.rest..")
    .adapter("persistence", "..adapter.persistence..")
    .withOptionalLayers(true)
```

The rule enforces three things:

- The domain doesn't depend on the application or on any adapter.
- The application doesn't depend on any adapter.
- Adapters don't depend on each other.

## Cycles and slices

```kotlin
@ArchTest
val no_package_cycles: ArchRule = slices()
    .matching("com.example.harnessengineeringspring.(*)..")
    .should().beFreeOfCycles()

@ArchTest
val features_are_independent: ArchRule = slices()
    .matching("..feature.(*)..")
    .should().notDependOnEachOther()
    .ignoreDependency(alwaysTrue(), resideInAPackage("..feature.shared.."))   // shared kernel allowed
```

- `ignoreDependency` above uses `DescribedPredicate.alwaysTrue` and `JavaClass.Predicates.resideInAPackage`.
- For irregular package layouts, use `slices().assignedFrom(object : SliceAssignment { ... })`, which maps each `JavaClass` to a `SliceIdentifier.of("name")` or to `SliceIdentifier.ignore()`.
- Cycle reports can be capped with `cycles.maxNumberToDetect` and `cycles.maxNumberOfDependenciesPerEdge` in `archunit.properties`.
- Modules with declared allowed dependencies: `ModuleRuleDefinition.modules().definedByPackages("..(*)..")` or `.definedByAnnotation(AppModule::class.java)` followed by `.respectTheirAllowedDependenciesDeclaredIn(...)` / `.onlyDependOnEachOtherThroughPackagesDeclaredIn(...)`. Use it only when slices aren't enough.

## Library rules (GeneralCodingRules, DependencyRules)

```kotlin
@ArchTest val no_field_injection: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
@ArchTest val no_std_streams: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_ACCESS_STANDARD_STREAMS
@ArchTest val no_generic_exceptions: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_THROW_GENERIC_EXCEPTIONS
@ArchTest val no_jul: ArchRule = GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING
@ArchTest val no_old_date_time: ArchRule = GeneralCodingRules.OLD_DATE_AND_TIME_CLASSES_SHOULD_NOT_BE_USED
@ArchTest val no_deprecated_api: ArchRule = GeneralCodingRules.DEPRECATED_API_SHOULD_NOT_BE_USED
@ArchTest val no_upper_package_deps: ArchRule = DependencyRules.NO_CLASSES_SHOULD_DEPEND_UPPER_PACKAGES
```

Library rules fail on empty input too: `NO_CLASSES_SHOULD_USE_FIELD_INJECTION` reports "failed to check any classes" when no class has a field at all, which happens in a fresh project. Add such rules once the code they guard exists; don't switch off `failOnEmptyShould` globally for them.

Other library rules: `NO_CLASSES_SHOULD_USE_JODATIME`, `ASSERTIONS_SHOULD_HAVE_DETAIL_MESSAGE`, `testClassesShouldResideInTheSamePackageAsImplementation()` (this one needs test classes imported), and `ProxyRules` (e.g. no direct self-calls to `@Transactional`/`@Async` methods, which bypass the Spring proxy).

## Spring conventions

```kotlin
@ArchTest
val controllers_live_in_web: ArchRule = classes()
    .that().areAnnotatedWith(RestController::class.java)
    .should().resideInAPackage("..web..")
    .andShould().haveSimpleNameEndingWith("Controller")

@ArchTest
val transactions_only_in_services: ArchRule = noClasses()
    .that().resideOutsideOfPackage("..service..")
    .should().beAnnotatedWith(Transactional::class.java)
    .because("transaction boundaries are use-case boundaries")

@ArchTest
val no_transactional_on_controller_methods: ArchRule = methods()
    .that().areDeclaredInClassesThat().areAnnotatedWith(RestController::class.java)
    .should().notBeAnnotatedWith(Transactional::class.java)

@ArchTest
val repositories_not_used_by_web: ArchRule = noClasses()
    .that().resideInAPackage("..web..")
    .should().dependOnClassesThat().areAssignableTo(Repository::class.java)   // org.springframework.data.repository.Repository

@ArchTest
val config_properties_are_immutable: ArchRule = fields()
    .that().areDeclaredInClassesThat().areAnnotatedWith(ConfigurationProperties::class.java)
    .should().beFinal()
```

- Annotations can also be named by their fully qualified name (`areAnnotatedWith("org.springframework.stereotype.Service")`), so no compile-time dependency is needed.
- `areMetaAnnotatedWith(Component::class.java)` matches all stereotypes (`@Service`, `@RestController`, ...).

## Naming and containment

```kotlin
@ArchTest
val services_are_named_service: ArchRule = classes()
    .that().resideInAPackage("..service..")
    .and().areTopLevelClasses()
    .and().areNotInterfaces()
    .and().haveSimpleNameNotEndingWith("Kt")          // Kotlin file facades of top-level functions
    .should().haveSimpleNameEndingWith("Service")
```

Kotlin compiles extra classes that naming rules will otherwise hit:

| Generated class | Comes from |
|---|---|
| `FooKt` | top-level functions/properties in `Foo.kt` |
| `Foo$Companion` | `companion object` |
| `Foo$WhenMappings` | `when` over an enum |
| `Foo$DefaultImpls` | interface default methods (without `-Xjvm-default=all`) |
| `Foo$bar$1`, `Foo$lambda...` | lambdas, anonymous objects |

Filter them out with `areTopLevelClasses()`, `areNotAnonymousClasses()`, `areNotMemberClasses()` or `haveNameNotMatching(".*\\$.*")`.

## Custom predicates and conditions

Use these only when the predefined API can't express the concept. Keep them `private` in the architecture test class, or in a shared `ArchRules` file.

```kotlin
private val haveManyConstructorParameters: DescribedPredicate<JavaClass> =
    DescribedPredicate.describe("have a constructor with more than 7 parameters") { javaClass: JavaClass ->
        javaClass.constructors.any { it.rawParameterTypes.size > 7 }
    }

private val notCallInstantNow = object : ArchCondition<JavaClass>("not call Instant.now()") {
    override fun check(item: JavaClass, events: ConditionEvents) {
        item.methodCallsFromSelf
            .filter { it.targetOwner.isEquivalentTo(java.time.Instant::class.java) && it.name == "now" }
            .forEach { events.add(SimpleConditionEvent.violated(it, "${it.description} - inject a Clock instead")) }
    }
}

@ArchTest
val no_god_constructors: ArchRule = noClasses().that(haveManyConstructorParameters).should()
    .beAnnotatedWith(Service::class.java).because("too many collaborators - split the service")

@ArchTest
val time_comes_from_clock: ArchRule = classes().should(notCallInstantNow)
```

- **Report every offender**: emit one `violated` event per offending item, using the access or member as the event's object, so the message includes `(File.kt:line)`.
- **Ready-made pieces**: `JavaClass.Predicates.*`, `HasName.Predicates.*`, `ArchConditions.*` (e.g. `ArchConditions.callMethod(...)`, `be(predicate)`, `have(predicate)`).
- **Combining**: join predicates with `.and()` / `.or()`. If generics clash, call `.forSubtype()` on the more general predicate.
- **Other concepts** (business modules, packages): use `all(ClassesTransformer)` with an `AbstractClassesTransformer<T>`.

## Bad vs good

```kotlin
// BAD: silent no-op after a package rename (only caught because empty-should fails by default)
classes().that().resideInAPackage("com.example.old..").should().haveSimpleNameEndingWith("Service")
    .allowEmptyShould(true)

// BAD: matches nothing - patterns match packages, not class names
noClasses().should().dependOnClassesThat().resideInAPackage("..UserRepository")

// BAD: re-imports the classpath per test, no caching, easy to forget .check(...)
@Test fun `layers`() { layeredArchitecture()/*...*/.check(ClassFileImporter().importPackages("com.example")) }

// GOOD: field rule, cached import, explicit reason
@ArchTest
val web_does_not_use_repositories: ArchRule = noClasses().that().resideInAPackage("..web..")
    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
    .because("controllers go through a service so transactions and validation are applied")
```
