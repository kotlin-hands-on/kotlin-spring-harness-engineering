# Generate with Exposed, apply with Flyway

The Exposed Gradle/Maven plugins compare **compiled Exposed table definitions** with a database schema and write SQL files. They are not production migration runners and do not apply newly generated files. Flyway owns application/deployment migration history, ordering, checksums, and execution.

Examples below target **plugin 1.6.0**. `tablesPackage` (single package) works in all plugin releases; `tablesPackages` (a collection, for tables spread over several packages) exists from **1.6.0** only. If both are set, the distinct combined values are used. On a plugin older than 1.6.0, use `tablesPackage` alone. Do not copy unreleased configuration from `main` into a pinned released build.

## Choose the comparison baseline

| Mode | Behavior | Your responsibility |
|---|---|---|
| `testContainersImageName` | Starts a disposable database, uses bundled Flyway to replay existing scripts from `fileDirectory`, diffs the resulting schema, writes new SQL, stops the container | Docker available; matching database family/version; complete committed migration history |
| `databaseUrl` + credentials | Connects via JDBC and compares the current schema; does **not** replay migration files first | Prepare a disposable database at the previous committed version before generation |

`testContainersImageName` takes precedence over direct connection settings. Supported documented container families are PostgreSQL, MySQL, MariaDB, SQL Server, and Oracle. An empty history produces an initial-schema diff; a database already updated to the new model may produce no changes and conceal the migration you needed.

Both plugins use **JDBC**, even for an application using Exposed R2DBC. Ensure the compiled table classes, their dependencies, and the appropriate JDBC driver are on the generator classpath. Scanning discovers table singleton objects in the configured package; arbitrary runtime-created `Table` instances are not automatically equivalent.

## Gradle plugin

Apply after the existing Kotlin JVM plugin in `build.gradle.kts`; do not add a second `plugins` block:

```kotlin
plugins {
    // Existing compatible Kotlin JVM plugin goes first.
    id("org.jetbrains.exposed.plugin") version "1.6.0"
}

exposed {
    migrations {
        tablesPackage.set("com.example.persistence.tables")
        // 1.6.0+, several packages instead of (or in addition to) tablesPackage:
        // tablesPackages.set(listOf("com.example.auth.tables", "com.example.billing.tables"))
        testContainersImageName.set("postgres:18-alpine")
        fileDirectory.set("src/main/resources/db/migration")
    }
}
```

Use the project's pinned PostgreSQL image (the image above is illustrative), matching JDBC driver, and [Exposed dependencies](build-setup.md). The default scan classpath is the main runtime classpath. An explicit override is `classpath.setFrom(sourceSets.main.get().runtimeClasspath)` inside `migrations`; in multi-module builds include the module that actually contains the compiled tables.

Direct JDBC alternative: replace `testContainersImageName` with these entries (environment values must refer to a **disposable baseline database**, never production):

```kotlin
databaseUrl.set(providers.environmentVariable("MIGRATION_DB_URL"))
databaseUser.set(providers.environmentVariable("MIGRATION_DB_USER"))
databasePassword.set(providers.environmentVariable("MIGRATION_DB_PASSWORD"))
```

Generate using the project wrapper and correct module path:

```bash
./gradlew :persistence:generateMigrations
```

For one explicitly named script (choose a **new** version each time):

```bash
./gradlew :persistence:generateMigrations --filename=V20260918140000__add_customer_email.sql
```

For a single-module build omit `:persistence`. Generation should be an explicit developer/CI action, not an automatic dependency of every `build`: it requires a database/Docker and writes source resources.

## Maven plugin

Merge into `build/plugins` in the Kotlin-configured `pom.xml`; reuse `exposed.version` from the dependency setup:

```xml
<plugin>
    <groupId>org.jetbrains.exposed.plugin</groupId>
    <artifactId>exposed-maven-plugin</artifactId>
    <version>${exposed.version}</version>
    <configuration>
        <tablesPackage>com.example.persistence.tables</tablesPackage>
        <!-- 1.6.0+, several packages (nested elements): -->
        <!-- <tablesPackages><package>com.example.auth.tables</package><package>com.example.billing.tables</package></tablesPackages> -->
        <testContainersImageName>postgres:18-alpine</testContainersImageName>
        <fileDirectory>src/main/resources/db/migration</fileDirectory>
    </configuration>
</plugin>
```

For direct JDBC mode, replace the image element with:

```xml
<databaseUrl>${env.MIGRATION_DB_URL}</databaseUrl>
<databaseUser>${env.MIGRATION_DB_USER}</databaseUser>
<databasePassword>${env.MIGRATION_DB_PASSWORD}</databasePassword>
```

Run from the module containing the table model (use `./mvnw` if available):

```bash
mvn exposed:generate-migrations
mvn exposed:generate-migrations -Dexposed.migrations.filename=V20260918140000__add_customer_email.sql
```

These commands are alternatives, not sequential steps. The goal forks compilation; ensure Kotlin compilation and reactor dependencies are correctly wired. Parameters use `-Dexposed.migrations.<name>`. The correct goal is plural **`generate-migrations`**, and the property prefix is **`exposed`**, despite typos in some documentation examples. A fully qualified invocation is `mvn org.jetbrains.exposed.plugin:exposed-maven-plugin:1.6.0:generate-migrations`.

An optional lifecycle binding uses phase `process-classes` and goal `generate-migrations`; prefer explicit invocation so ordinary builds do not unexpectedly require a database or rewrite migration resources.

## Naming and output

| Setting | Default |
|---|---|
| `fileDirectory` | `src/main/resources/db/migration` |
| `filePrefix` | `V` |
| `fileVersionFormat` | `TIMESTAMP_ONLY` |
| `fileSeparator` | `__` |
| `useUpperCaseDescription` | `true` |
| `fileExtension` | `.sql` |

Version formats: `TIMESTAMP_ONLY`, `TIMESTAMP_WITHOUT_SECONDS`, `MAJOR_TIMESTAMP`, `MAJOR_TIMESTAMP_WITHOUT_SECONDS`, `MAJOR_MINOR`, `MAJOR_ONLY`. For a Gradle enum setting (import path verified against 1.5.0 sources; recheck on 1.6.0 if the import fails), import `org.jetbrains.exposed.v1.plugin.core.migration.VersionFormat` (not the obsolete Gradle-package import).

Keep Flyway-compatible `V<unique-version>__<description>.sql` names. The container replay uses `fileDirectory` as a filesystem location; custom filename conventions are not automatically propagated to its Flyway configuration. Likewise, do not assume application Flyway placeholders, schemas, or custom Java migrations are automatically configured in the plugin's embedded replay. If the bundled replay cannot represent your baseline, prepare it externally and use direct JDBC mode.

**An explicitly reused filename can overwrite an existing script.** Plugin 1.5.0+ fixes timestamp collisions seen in 1.4.0 by adding indexed suffixes for subsequent generated files, but this does not make arbitrary filename reuse safe. Passing `--filename` also merges every statement into **one** script even when several tables changed. Check the working-tree diff and Flyway version uniqueness, especially when merging parallel branches.

## Safe end-to-end workflow

1. Start from clean, committed migration history and choose the production dialect/version. Use plugin-managed Testcontainers replay, or restore a disposable database and migrate it to the last committed version yourself.
2. Change the Exposed table definitions. Do not call `SchemaUtils` or enable startup DDL to update the comparison database first.
3. Compile the table model and run the appropriate generation command. Confirm the expected tables were discovered and the correct database/schema was compared.
4. Review every statement. Hand-author renames, data backfills, staged non-null constraints, and dialect-specific changes where the diff cannot express intent. For zero-downtime changes, use an expand/backfill/contract sequence compatible with old and new application versions.
5. Verify the new filename/version is unique. Never edit a migration already applied to shared environments; create a follow-up instead.
6. Test the complete history on an empty database and an upgrade from the previous schema containing representative rows. Assert data preservation and constraints, not merely successful DDL execution.
7. Commit reviewed SQL with the table changes. Apply it separately through the existing Flyway deployment/application integration; run Flyway validation and persistence smoke tests. No production migration or destructive command should run merely because this skill was invoked.

## Limits and troubleshooting

- Generated changes may include destructive `DROP` statements. A rename can look like add-and-drop; a successful schema diff is not evidence of data safety.
- Documented migration detection is incomplete: column-type changes are supported only for H2 in the reviewed documentation; constraint/default-expression detection has gaps; SQLite has ALTER limitations. Verify each important change manually on the target dialect.
- Generator fixes in 1.6.0: distinct indices are no longer treated as duplicates (one was being dropped), and child tables referencing an indexed parent no longer emit duplicate `ALTER` statements. If an older plugin produced a missing index or doubled `ALTER`, review the SQL by hand or upgrade the plugin. Also 1.6.0: DDL for column defaults built from `Op` on MariaDB and from `Function` on SQLite.
- No output: check the package name, compilation, runtime classpath, singleton table definitions, selected schema, and whether the baseline was already advanced.
- Initial-create statements instead of an incremental diff: check the migration input directory, connection/schema, and successful baseline replay.
- Missing driver/table classes: fix the generator's runtime classpath rather than adding random libraries to application source dependencies.
- SQL generation failure on custom Flyway history: reproduce the baseline externally; the embedded Flyway setup does not automatically inherit the application's configuration.
- The migration library API (`exposed-migration-core` plus the relevant JDBC/R2DBC module) is a separate option for custom tooling, not the plugin itself. Confirm `MigrationUtils` signatures/imports for the chosen version before writing a custom runner.
- There is **no dedicated Kotlin Toolchain migration plugin yet**. Use a supported Gradle/Maven helper loading the same compiled schema model; do not invent a Toolchain-specific DSL.

## Sources and version evidence

- [Exposed Gradle plugin](https://www.jetbrains.com/help/exposed/exposed-gradle-plugin.html)
- [Exposed Maven plugin](https://www.jetbrains.com/help/exposed/exposed-maven-plugin.html)
- [Migration APIs and limitations](https://www.jetbrains.com/help/exposed/migrations.html)
- [Gradle Plugin Portal](https://plugins.gradle.org/plugin/org.jetbrains.exposed.plugin)
- [1.5.0 Gradle configuration source](https://github.com/JetBrains/Exposed/blob/1.5.0/exposed-gradle-plugin/src/main/kotlin/org/jetbrains/exposed/v1/gradle/plugin/MigrationsExtension.kt)
- [1.5.0 Maven goal and parameters](https://github.com/JetBrains/Exposed/blob/1.5.0/exposed-maven-plugin/src/main/kotlin/org/jetbrains/exposed/v1/maven/plugin/GenerateMigrationsMojo.kt)
- [1.5.0 scanner, Flyway replay, and SQL writer](https://github.com/JetBrains/Exposed/blob/1.5.0/exposed-plugin-core/src/main/kotlin/org/jetbrains/exposed/v1/plugin/core/migration/MigrationGenerator.kt)
- [Timestamp collision fix](https://github.com/JetBrains/Exposed/pull/2898)
- [Multiple table packages (1.6.0)](https://github.com/JetBrains/Exposed/pull/2928)
- [Changelog 1.6.0](https://github.com/JetBrains/Exposed/blob/main/CHANGELOG.md)