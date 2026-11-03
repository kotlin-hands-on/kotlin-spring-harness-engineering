# Build setup and version alignment

## Dependency choices

Use Maven Central. All Exposed application artifacts use group `org.jetbrains.exposed`; pin them to one compatible version (examples use the reviewed `1.6.0` release).

| Need | Modules |
|---|---|
| SQL types, tables, expressions | `exposed-core` |
| Blocking JDBC execution | `exposed-jdbc` plus the database's JDBC driver |
| Non-blocking execution | `exposed-r2dbc` plus the database's R2DBC driver |
| DAO entities (JDBC) | `exposed-dao` **and `exposed-jdbc`**, not R2DBC |
| DAO entities (R2DBC), **experimental, 1.6.0+** | `exposed-dao-r2dbc` **and `exposed-r2dbc`**; API may change or be dropped; never in the same source set as `exposed-dao` |
| `java.time` columns | `exposed-java-time` |
| Kotlin date/time columns | `exposed-kotlin-datetime`; check the release's `kotlin.time`/`kotlinx.datetime` type mappings |
| Joda-Time columns | `exposed-jodatime` |
| JSON columns | `exposed-json` plus the chosen serializer/configuration |
| Money, encryption/hashing | `exposed-money`, `exposed-crypt` (hashing algorithms added in 1.5.0) |
| Programmatic migration generation | `exposed-migration-core` plus `exposed-migration-jdbc` or `exposed-migration-r2dbc` |
| Spring transaction integration | The matching Boot starter or Spring transaction module from [Spring Boot](spring-boot.md) |

Choose one transport for each persistence path rather than adding both to fix unresolved imports. JDBC and R2DBC drivers are not interchangeable. Adding a driver or Exposed does not automatically configure a production connection pool.

## Gradle Kotlin DSL

Merge into an existing Kotlin JVM project; keep the project's compatible Kotlin/JDK configuration. This is a minimal **JDBC dependency fragment**, not a complete application build:

```kotlin
repositories {
    mavenCentral()
}

val exposedVersion = "1.6.0"

dependencies {
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    // Add only when using entities:
    // implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    runtimeOnly("com.h2database:h2:2.4.240") // Demo/test driver; replace for production.
}
```

For R2DBC, replace `exposed-jdbc` with `exposed-r2dbc` and supply a compatible R2DBC driver, for example `org.postgresql:r2dbc-postgresql` at an explicitly managed version (Exposed 1.6.0 itself builds against 1.1.3.RELEASE; note 1.5.0 added handling for nullable enum columns with r2dbc-postgresql 1.0.5 or older). Do not add `exposed-dao`; for entities on R2DBC use the experimental `exposed-dao-r2dbc` instead. Migration build plugins still need JDBC even if the application uses R2DBC; isolate that classpath where practical.

### Optional published version catalog

Available since Exposed 1.4.0. Merge into `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("exposedLibs") {
            from("org.jetbrains.exposed:exposed-version-catalog:1.6.0")
        }
    }
}
```

Then use `implementation(exposedLibs.core)` and `implementation(exposedLibs.jdbc)` **instead of** the corresponding string coordinates. Accessors drop the `exposed-` prefix and turn dashes into nested accessors (`exposed-kotlin-datetime` → `exposedLibs.kotlin.datetime`). All modules share one `exposed` version entry, overridable with `version("exposed", "...")`. Inspect the published catalog for other aliases rather than guessing them. Gradle only; Maven declares artifacts directly. Do not name the catalog `exposed`: it conflicts with the migration plugin's `exposed { ... }` extension. If repositories are centralized in settings, omit the project-level `repositories` block above.

## Maven

Merge into a project already configured to compile Kotlin; Maven Central is available by default:

```xml
<properties>
    <exposed.version>1.6.0</exposed.version>
</properties>

<dependencies>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-core</artifactId>
        <version>${exposed.version}</version>
    </dependency>
    <dependency>
        <groupId>org.jetbrains.exposed</groupId>
        <artifactId>exposed-jdbc</artifactId>
        <version>${exposed.version}</version>
    </dependency>
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <version>2.4.240</version>
        <scope>runtime</scope>
    </dependency>
</dependencies>
```

Use the same version property for optional Exposed modules. R2DBC substitution rules are the same as for Gradle. Spring Boot dependency management may manage the database driver; do not assume it manages the desired Exposed version.

## Generation plugin requirements are separate

For the **1.6.0 migration build plugins**, the official documentation lists Kotlin 2.2+, JVM 11+, and Gradle 8.14+ or Maven 3.9+. These are plugin minima, not a complete compatibility matrix: the selected Gradle runtime, Kotlin release, or Spring Boot version can require a newer JDK. Use the strictest applicable requirement.

The Gradle plugin was introduced in 1.3.0; the Maven plugin in 1.4.0. Keep plugin and Exposed library versions aligned unless the relevant release explicitly documents another supported combination. See [migration plugin configuration](migrations.md), not the application dependency table, for plugin IDs and goals.

There is **no dedicated Kotlin Toolchain migration plugin yet** in the official integrations reviewed. Normal dependency consumption and custom execution are separate questions. For generation, use a small Gradle/Maven helper able to load the compiled table model and driver; do not invent a `module.yaml` migration task or duplicate schema definitions by hand.

## Upgrading from pre-1.0 examples

- Use `org.jetbrains.exposed.v1.core` for common types, `v1.jdbc` or `v1.r2dbc` for execution, and `v1.dao` for entities. `Table` and `Database` no longer live together in the old `sql` package.
- Migration libraries were split: old `exposed-migration` examples must be adapted to the core/transport-specific modules. Their `MigrationUtils` imports also depend on the transport.
- Query syntax is `select(columns).where { ... }` / `selectAll().where { ... }`, not legacy `slice(...).select { ... }`.
- Coroutine transaction APIs changed across releases; use the matching release's signatures rather than mixing experimental `newSuspendedTransaction` snippets with current `suspendTransaction` examples.
- Check date/time and UUID mappings, dialect-specific changes, and the Spring 6/7 split in the migration guide. Compile and run persistence/migration integration tests after upgrading.

## Sources

- [Adding dependencies](https://www.jetbrains.com/help/exposed/adding-dependencies.html)
- [Official module list](https://github.com/JetBrains/Exposed/tree/1.6.0)
- [Published version catalog](https://github.com/JetBrains/Exposed/tree/1.6.0/exposed-version-catalog)
- [Gradle plugin requirements](https://www.jetbrains.com/help/exposed/exposed-gradle-plugin.html)
- [Maven plugin requirements](https://www.jetbrains.com/help/exposed/exposed-maven-plugin.html)
- [Migration guide to v1](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)