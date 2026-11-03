# Design

## Context

See proposal.md - Why / What Changes. Observed current state:

- Single Gradle project at the repo root (`build.gradle.kts`, `settings.gradle.kts`, `src/`), Gradle 9.8.2 wrapper, Kotlin 2.4.21, Spring Boot 4.1.2 with `io.spring.dependency-management`, JDK toolchain 25.
- Root build already carries the project policy that must survive the split: Detekt `2.0.0-alpha.6` (+ ktlint wrapper, `config/detekt/detekt.yml`, `buildUponDefaultConfig`, optional `detekt-baseline.xml`, Kotlin `2.4.10` pin on the `detekt` configuration, SARIF/HTML reports, build scripts as Detekt sources), power-assert with the `kotlin.test` function list, the compiler flags (`-Xjsr305=strict -Wextra -Xcollection-literals -Xconsistent-data-class-copy-visibility -Xexplicit-context-arguments -Xname-based-destructuring=complete -Xreturn-value-checker=check -Xintrinsic-const-evaluation`), JUnit 6.1.3 via `extra["junit-jupiter.version"]`, test logging with `TestExceptionFormat.FULL`.
- Production code: only `HarnessEngineeringSpringApplication` (package `com.example.harnessengineeringspring`). Tests: `HarnessEngineeringSpringApplicationTests` (`@SpringBootTest contextLoads`) and `JUnitSetupTest`.
- `jackson-module-kotlin` (Jackson 3, `tools.jackson`) is declared explicitly. Actuator is present.
- Harness hooks (`.claude/hooks/`) discover Gradle projects by the presence of a build file, run `:<module>:detekt` on edited `.kt/.kts` files and `:<module>:check` at stop time. They already treat `gradle/`, `build-logic/` and root build files as global inputs and skip `build-logic/` as a module. `preflight.py` reads the JDK version from `jvmToolchain(N)` in the **root** `build.gradle.kts`.
- No specs exist yet (`openspec/specs/` is empty); all other active changes build on this one. This change sets `skip_specs: true`, so the verification scenarios below take the place of spec scenarios and are referenced by `tasks.md`.

## Goals / Non-Goals

**Goals:**

- Three Gradle modules `:shared` (KMP `jvm` + `wasmJs`), `:backend` (Spring Boot), `:web` (Compose Multiplatform, `wasmJs` browser executable), with all build policy in one place so every module gets Detekt, power-assert, compiler flags and test settings identically.
- A backend that starts against Postgres (Flyway + Exposed on the Spring-managed datasource), serves application JSON with kotlinx.serialization, and serves a static SPA bundle with fallback.
- One shared Spring test context with a Testcontainers Postgres, plus an initial ArchUnit test.
- Each of the above observable by an automated test or a precise verification command.

**Non-Goals:**

- No tables, migrations, DTOs, endpoints (beyond a test-only probe controller), UI or routing. The first migration comes from `groups-and-members`; the client runtime from `web-shell`.
- No CI pipeline, no dev-server proxy, no container image build.
- No change to the root package/group name or to the Detekt rule set.
- No changes to `.claude/` harness files other than `.claude/hooks/preflight.py` (D11, decided at the plan gate).
- No money code: this change only puts a JSR-354 implementation on the classpath (D4).

## Decisions

### D1 - Build logic in a `build-logic` included build with precompiled convention plugins, versions in `gradle/libs.versions.toml`

Conventions (plugin ids are a suggestion, names are free):

- `conventions.kotlin-base` - applied by every Kotlin module: Detekt (same settings as today, Kotlin pin on the `detekt` configuration, reports), power-assert function list, test task settings (`useJUnitPlatform`, logging), `jvmToolchain(<jdk>)` where `<jdk>` is read from the catalog entry `jdk = "25"` in `gradle/libs.versions.toml` (single source of truth, also read by `preflight.py`, D11).
- `conventions.kotlin-jvm` - `kotlin("jvm")` + base + all compiler flags on JVM compilations.
- `conventions.kotlin-multiplatform` - `kotlin("multiplatform")` + base; platform-neutral flags (`-Wextra`, collection literals, consistent copy visibility, explicit context arguments, name-based destructuring, return-value checker, intrinsic const evaluation) on **all** compilations, `-Xjsr305=strict` only on JVM compilations.
- Spring/Compose specifics stay in the module build files (`:backend` applies Boot, dependency-management, `plugin.spring`; `:web` applies Compose), so the conventions don't carry module-specific plugins.

The root project keeps Detekt only, with `settings.gradle.kts` and the root `build.gradle.kts` as sources - the detekt hook maps `settings.gradle.kts` edits to the root `:detekt` task.

Why: the proposal requires the policy "applied to every module"; a convention plugin is the single place that guarantees it (`authoring-gradle-builds`: favour `build-logic` over `buildSrc` and over `subprojects {}`). The hooks already treat `build-logic/` and `gradle/` as global inputs, so editing a convention re-checks all modules.
Alternatives: `subprojects {}` / `allprojects {}` in the root (cross-project configuration, breaks isolated projects, discouraged); `buildSrc` (invalidates everything on change); copying the block into each module (drift).

### D2 - Detekt covers every Kotlin source set of a module through the plain `detekt` task

For KMP modules the plain `detekt` task does not pick up `src/commonMain/kotlin` etc. by default. The base convention sets the `detekt` source to all `src/*/kotlin` directories of the module plus its own `build.gradle.kts`, and `check` depends on `detekt`.
Why: the edit hook runs exactly `:<module>:detekt` and filters by file, the stop hook runs `:<module>:check`; both must see KMP sources. Type-resolved per-source-set tasks (`detektJvmMain`, ...) may additionally exist but are not what the harness runs.
Alternative: wiring `check` to the per-source-set tasks only - the edit hook would then silently report nothing for KMP files.

### D3 - Module layout and packages

- `:backend` - existing `src/` moved here unchanged (package `com.example.harnessengineeringspring`); depends on `:shared` (`implementation(project(":shared"))`) so the contract later added by `api-contract-foundation` is on the backend classpath.
- `:shared` - KMP `jvm()` + `wasmJs { nodejs() }`, `kotlin("plugin.serialization")`, `kotlinx-serialization-json` in `commonMain`; package root `com.example.harnessengineeringspring.shared`. No production code in this change.
- `:web` - KMP `wasmJs { browser(); binaries.executable() }`, `org.jetbrains.compose` + `org.jetbrains.kotlin.plugin.compose`, depends on `:shared`; package root `com.example.harnessengineeringspring.web`. Contains only a placeholder entry point (`ComposeViewport` with empty or placeholder content, no `!!`) and `index.html` loading the generated script - enough for `wasmJsBrowserDistribution` to produce a runnable bundle.
- `settings.gradle.kts`: `pluginManagement { includeBuild("build-logic") }`, `include(":shared", ":backend", ":web")`, repositories `mavenCentral()` + `google()` (Compose) centralised in `dependencyResolutionManagement`. **Not** `RepositoriesMode.FAIL_ON_PROJECT_REPOS`: the Kotlin JS/Wasm plugin adds its own Node/Yarn download repositories at project level.
- No Kotlin/Wasm yarn lock is committed in this change: while `:web` has no npm dependencies the toolchain writes only a two-line header to `build/wasm/yarn.lock` and nothing to `kotlin-js-store/` (decided at review round 1). The lock under `kotlin-js-store/` is committed once the first npm dependency appears (`web-shell`).
Why `wasmJs { nodejs() }` in `:shared`: tests in `commonTest` then run on JVM and on Wasm without a headless browser; wasmJs klibs are consumable by the browser app. Alternative: `browser()` (needs Chrome/Karma on every dev machine) - see Open questions.

### D4 - Persistence: Exposed DSL over the Spring-managed datasource, Flyway applies migrations

- `exposed-spring-boot4-starter`, `exposed-core`, `exposed-jdbc`, `exposed-json`, `exposed-money`, all `1.6.0` from one catalog version; `org.postgresql:postgresql` (runtime, Boot-managed). No `exposed-dao` (DSL only, see proposal).
- Application class: `@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])` with no explicit Exposed import, so Exposed's `SpringTransactionManager` is the single transaction manager. The Exposed starter registers `ExposedAutoConfiguration` itself via its `AutoConfiguration.imports`, and an `exclude` on `@ImportAutoConfiguration` would only filter what that annotation imports, not the application's own auto-configuration candidates. The persistence smoke test asserts that exactly one transaction manager exists and that it is Exposed's `SpringTransactionManager` (P3).
- JSR-354 implementation: `org.javamoney:moneta` `1.4.5` (latest release, 2025-03-22; catalog version `moneta`) as `implementation` in `:backend`, so `exposed-money`'s `javax.money` API has a runtime provider (`Monetary.*` resolves). Moneta is a pom-packaged aggregator of `moneta-core`/`moneta-convert`; verify with `inspect_dependencies` that it resolves on `:backend:runtimeClasspath` and that its `org.javamoney:moneta-core` and `javax.money:money-api` versions are consistent with what `exposed-money` pulls in (align via catalog constraint if not). No money code, columns or converters are written here; later changes use it.
- `spring.exposed.generate-ddl=false`; schema changes only via Flyway.
- `spring-boot-starter-flyway` + `org.flywaydb:flyway-database-postgresql` (Boot-managed versions); migrations in `backend/src/main/resources/db/migration/` (kept in git with a `.gitkeep` until the first migration).
- Datasource properties in `application.properties` with env overrides and defaults matching docker-compose: `spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/app}`, `username=${DB_USER:app}`, `password=${DB_PASSWORD:app}`.
Why: one transaction owner (Spring), schema history owned by Flyway, Exposed never creates DDL at runtime.
Alternatives: Exposed `transaction {}` blocks with a separate `Database.connect()` (second connection owner); `SchemaUtils.create` (no versioned history).

### D5 - Exposed Gradle migration plugin in `:backend`, generation is explicit only

`id("org.jetbrains.exposed.plugin")` `1.6.0` in `:backend`:

```kotlin
exposed {
    migrations {
        tablesPackage.set("com.example.harnessengineeringspring.persistence")
        testContainersImageName.set("postgres:18-alpine")
        fileDirectory.set("src/main/resources/db/migration")
    }
}
```

`generateMigrations` is never wired into `build`, `check`, `assemble` or `processResources`. The Postgres image tag is the same in the plugin, the Testcontainers config (D6) and `compose.yaml` (D7).
Why: generation needs Docker and writes into `src/` (proposal: "never part of `build`"). Testcontainers mode replays existing Flyway scripts, so the baseline is the committed history.
Alternative: `databaseUrl` mode against the compose database - the baseline would drift with whatever state the local DB is in.

### D6 - One shared Spring test context with Testcontainers Postgres

- `backend/src/test/kotlin/.../support/IntegrationTest.kt`: meta-annotation `@SpringBootTest @AutoConfigureMockMvc @Import(TestcontainersConfig::class)` - every Spring test uses it unchanged (`spring-testing`).
- `TestcontainersConfig`: `@TestConfiguration(proxyBeanMethods = false)` with `@Bean @ServiceConnection fun postgres() = PostgreSQLContainer("postgres:18-alpine")` (Testcontainers 2.x artifact `org.testcontainers:testcontainers-postgresql`, Boot-managed; plus `spring-boot-testcontainers`).
- The old `contextLoads` test is replaced by the persistence smoke test (a context without Postgres can no longer start).
- No `@MockitoBean`/`@MockkBean`/`@DirtiesContext`, no per-class properties or profiles.

### D7 - Local dev environment: `compose.yaml` at the repo root

Single service `postgres` (`postgres:18-alpine`), `POSTGRES_DB/USER/PASSWORD=app`, port `5432:5432`, named volume, `pg_isready` healthcheck. No `spring-boot-docker-compose` dependency: `bootRun` does not start containers implicitly; the developer runs `docker compose up -d`.
Alternative: `spring-boot-docker-compose` (auto-start on `bootRun`) - convenient, but adds a hidden side effect to every run and interacts with tests; can be added later.

### D8 - kotlinx.serialization on the application HTTP path

- `:backend` applies `kotlin("plugin.serialization")`, adds `spring-boot-starter-kotlinx-serialization-json`, and does **not** set `spring.http.converters.preferred-json-mapper`: in Boot 4.1.2 its accepted values are only `gson`, `jackson`, `jackson2` and `jsonb` (configuration metadata), there is no kotlinx value. With `spring-boot-starter-kotlinx-serialization-json` on the classpath and `jackson-module-kotlin` removed, the kotlinx converter is selected on its own; J1/J2 guard that selection against a Jackson regression. Boot auto-configures the `Json` bean, `spring.kotlinx.serialization.json.*` customises it.
- `tools.jackson.module:jackson-module-kotlin` is removed. Jackson itself stays on the runtime classpath because Actuator serialises its endpoints with its own isolated Jackson mapper (Boot 4.1 reference) - "remove Jackson from the HTTP path" is interpreted as: no application endpoint or application code uses Jackson (enforced by ArchUnit, D9).
- `kotlinx-serialization-json` resolves to the same version in `:shared` and `:backend` (catalog version; override Boot's managed version property if it differs). Checked with Gradle `inspect_dependencies`.
- Contract-level `Json` settings (class discriminator, explicit nulls, unknown keys) are **not** decided here; they belong to `api-contract-foundation`.

### D9 - Initial ArchUnit test encodes this change's decisions

`com.tngtech.archunit:archunit-junit6:1.5.1` in `:backend` tests; `backend/src/test/kotlin/com/example/harnessengineeringspring/architecture/ArchitectureTest.kt` with `@AnalyzeClasses(packagesOf = [HarnessEngineeringSpringApplication::class], importOptions = [ImportOption.DoNotIncludeTests::class])`; `src/test/resources/archunit.properties` with `junit.displayName.replaceUnderscoresBySpaces=true`. Rules (each with `.because(...)`):

1. `no_production_code_uses_jackson` - no class depends on `com.fasterxml.jackson..` or `tools.jackson..` (D8).
2. `persistence_uses_exposed_dsl_only` - no class depends on `org.jetbrains.exposed.v1.dao..` (D4).
3. `no_field_injection` - `GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION`.

Cycle/layer rules are deliberately deferred: on a codebase without subpackages they fail with "failed to check any classes" (`archunit` skill: add each rule with the first code it guards).

### D10 - SPA serving: Spring MVC resource handling with index fallback; bundle packaged into the boot jar only

- A `WebMvcConfigurer` (backend, package e.g. `...web`) registers `classpath:/static/` for `/**` with a resource resolver that falls back to `static/index.html` when the requested resource does not exist **and** the last path segment has no file extension **and** the path is not under `/api/` or `/actuator/`. Controllers keep precedence over the resource handler.
- `.wasm` is served as `application/wasm` (needed for streaming compilation); add an explicit MIME mapping only if the embedded server lacks it.
- `:web` exposes its `wasmJsBrowserDistribution` output through a consumable configuration; `:backend` consumes it in a resolvable configuration and adds it to `bootJar` under `BOOT-INF/classes/static/`. `processResources`/`test` of `:backend` do **not** depend on the web build, so backend tests stay fast and run without Node; the SPA tests use fixtures in `backend/src/test/resources/static/` (`index.html` with a marker string, a tiny `probe.wasm`).
Why: the proposal scopes this to wiring; tests can exercise the fallback rules black-box without building Wasm.
Alternatives: copy the bundle into `src/main/resources` (generated files in source, stale bundles); a catch-all `@Controller` forwarding to `index.html` (fights the resource handler, easy to swallow `/api` 404s); wiring the bundle into `processResources` (every backend test run would build Wasm).

### D11 - `preflight.py` reads the JDK version from the version catalog

`.claude/hooks/preflight.py::required_jdk` currently matches `jvmToolchain(N)` in the root `build.gradle.kts`, which no longer contains it after D1. It is changed to read `jdk = "N"` from the `[versions]` table of `gradle/libs.versions.toml` (the same entry the convention plugin uses for `jvmToolchain`). Behaviour is otherwise unchanged: silent when the file or entry is missing (returns `None`), and the problem message keeps naming the required JDK, now phrased without the literal `jvmToolchain(N)` call. Only local parsing, no Gradle invocation, no network, so the hook stays fast. No other hook is touched (they already treat `gradle/` and `build-logic/` as global inputs).
Why: after the split the JDK check would silently stop working; the catalog entry is the one place that already has to exist for the convention plugin.
Alternative: parse `build-logic/**/*.kts` for `jvmToolchain(N)` - fragile and couples the hook to convention file names.

## Verification scenarios and test boundaries

`tasks.md` references these IDs. Test boundary in brackets, followed by what it catches / misses.

**Build structure** [Gradle build verification command] - catches missing modules, convention not applied, broken task wiring; misses runtime behaviour.

- **B1** WHEN `./gradlew projects` runs THEN it lists exactly `:backend`, `:shared`, `:web`.
- **B2** WHEN a deliberate Detekt violation is placed in a Kotlin source file of a module (backend `main`, shared `commonMain`, web `wasmJsMain`) or in its `build.gradle.kts` THEN `:<module>:detekt` and `:<module>:check` fail and name that file; WHEN it is reverted THEN both pass. Same for `settings.gradle.kts` with root `:detekt`.
- **B3** WHEN `./gradlew build --dry-run` runs THEN `generateMigrations` is not in the task list. WHEN `./gradlew :backend:generateMigrations` runs with Docker available THEN it completes against a Postgres container and writes no file while no table exists.
- **B4** WHEN `./gradlew :web:wasmJsBrowserDistribution` runs THEN the distribution contains `index.html`, at least one `.js` and one `.wasm` file.
- **B5** WHEN `./gradlew :backend:bootJar` runs THEN the jar contains `BOOT-INF/classes/static/index.html` and a `.wasm` file from `:web`. WHEN `./gradlew :backend:test --dry-run` runs THEN no `:web:` task is listed.
- **B6** WHEN `docker compose config` runs THEN it is valid; WHEN `docker compose up -d` and `./gradlew :backend:bootRun` run THEN `GET http://localhost:8080/actuator/health` returns `UP`.
- **B7** WHEN `inspect_dependencies` runs on `:backend:runtimeClasspath` and `:shared:jvmRuntimeClasspath` THEN `kotlinx-serialization-json` resolves to the same version, all `org.jetbrains.exposed` modules to `1.6.0`, `jackson-module-kotlin` is absent, `org.javamoney:moneta` resolves to `1.4.5` on `:backend:runtimeClasspath` with a single consistent `javax.money:money-api`, and Jupiter/Platform resolve to 6.x on every test runtime classpath.

**Shared module** [`commonTest`, run as `:shared:jvmTest` and `:shared:wasmJsTest`] - catches serialization plugin/runtime missing on a target and missing compiler flags; misses JVM-backend integration.

- **SH1** WHEN a test-only `@Serializable` class with a `@SerialName`-renamed property is encoded and decoded with `Json` THEN the JSON uses the serial name and the round trip yields an equal value, on JVM and Wasm.
- **SH2** WHEN the test source uses a collection literal and name-based destructuring THEN it compiles and passes on both targets (proves the shared compiler flags reach every compilation).

**Persistence** [shared `@IntegrationTest` context with Testcontainers Postgres; Spring beans and MockMvc] - catches datasource, Flyway, Exposed and transaction-manager wiring against real Postgres; misses migration content (none yet).

- **P1** WHEN the shared context starts THEN `GET /actuator/health` returns 200 with `"status":"UP"` (datasource health included in the aggregate).
- **P2** WHEN the context has started THEN the `Flyway` bean reports no pending and no failed migrations, and the schema history is on the container database.
- **P3** WHEN `SELECT 1` is executed with Exposed inside a `TransactionTemplate` built on the context's `PlatformTransactionManager` THEN it returns 1, and that transaction manager is Exposed's `org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager`.

**Money provider** [plain JUnit test in `:backend` (no Spring context), black-box through the `javax.money.Monetary` API] - catches a missing/unregistered JSR-354 implementation (`MonetaryException: No MonetaryAmountsSingletonSpi loaded`); misses Exposed money columns (later changes).

- **M1** WHEN `Monetary.getDefaultAmountFactory().setCurrency("EUR").setNumber(<decimal>).create()` is called THEN it returns a `MonetaryAmount` with currency `EUR` and the given number (no exception).
- **M2** WHEN `Monetary.getCurrency("EUR")` is called THEN it returns a unit with currency code `EUR` and 2 default fraction digits.

**Harness preflight** [command-line run of the hook with a JSON event on stdin and a temporary project root] - catches the JDK check silently disabled or crashing; misses real session start behaviour.

- **H1** WHEN `preflight.py` runs with `cwd` set to a temporary git repo whose `gradle/libs.versions.toml` has `jdk = "99"` THEN the output reports `No JDK 99` as a problem. WHEN the entry is `jdk = "25"` and a JDK 25 is installed, or the file/entry is absent, THEN the output contains no JDK problem and the hook exits 0. WHEN run on this repo THEN it exits 0 and reports no JDK problem.

**JSON on HTTP** [shared `@IntegrationTest` context + MockMvc against a test-only probe controller under `/api/__probe` in test sources] - catches the wrong converter being selected; misses contract-level `Json` settings (`api-contract-foundation`).

- **J1** WHEN `GET /api/__probe/json` returns a `@Serializable` value whose property carries `@SerialName("serial_name")` THEN the response is `application/json` and contains the key `serial_name` (Jackson would emit the Kotlin property name).
- **J2** WHEN `POST /api/__probe/json` sends `{"serial_name": "x"}` THEN the probe echoes the decoded value, i.e. request bodies are read by kotlinx.serialization.

**Architecture** [ArchUnit on `:backend` production bytecode] - catches structural drift from D4/D8/D9; misses behaviour.

- **A1** WHEN `ArchitectureTest` runs THEN rules 1-3 of D9 pass on the production code; WHEN a production class temporarily imports a Jackson type / an Exposed DAO type / uses field injection THEN the corresponding rule fails (verified once, then reverted).

**SPA serving** [shared `@IntegrationTest` context + MockMvc, fixtures in `backend/src/test/resources/static/`] - catches fallback rules and MIME type; misses the real bundle (covered by B4/B5).

- **S1** WHEN `GET /` THEN 200, `text/html`, body contains the fixture marker.
- **S2** WHEN `GET /some/client/route` (no extension) THEN 200, body is `index.html` (marker present).
- **S3** WHEN `GET /probe.wasm` THEN 200 with content type `application/wasm`.
- **S4** WHEN `GET /missing.js` THEN 404 (no fallback for paths with an extension).
- **S5** WHEN `GET /api/does-not-exist` THEN 404 and the body does not contain the marker.
- **S6** WHEN `GET /actuator/does-not-exist` THEN 404 and the body does not contain the marker.

## Risks / Trade-offs

- [Kotlin 2.4.21 vs Compose Multiplatform 1.12.x / Detekt 2.0 alpha compatibility] -> verify with the first `:web` and `:shared` build; pin the newest stable Compose release compatible with Kotlin 2.4.21; record any forced version in the catalog with a comment.
- [Experimental compiler flags not accepted by the Wasm backend] -> D1 splits JVM-only flags; if a frontend flag is rejected on Wasm, stop and report (policy says the flags apply everywhere) rather than silently dropping it.
- [Wasm builds download Node/Yarn on first run; `./gradlew build` now needs network once and is slower] -> accepted; backend tests do not depend on the web build (D10).
- [Exposed migration plugin may fail when `tablesPackage` has no tables yet] -> B3 is executed; if it fails only because no tables exist, record the exact message in the task and leave the verification of generated output to `groups-and-members`.
- [Same Postgres tag in three places (plugin, Testcontainers, compose)] -> one task updates all three; reviewers check equality. A single source of truth is a follow-up.
- [Docker required for `:backend:test`] -> project policy; tests fail fast with a Testcontainers error when Docker is missing.
- [Jackson remains on the classpath for Actuator] -> ArchUnit rule 1 keeps application code off it; the converter preference keeps `@Serializable` bodies on kotlinx.
- [`preflight.py` no longer finds `jvmToolchain(N)` in the root build file] -> D11 switches it to the `jdk` catalog entry; H1 verifies it, and the catalog task (1.1) introduces the entry so the hook is never out of sync with the build.
- [Moneta is a pom aggregator and may pull a `money-api` version that differs from `exposed-money`'s] -> B7 checks for a single consistent version; align via a catalog constraint if needed.

## Migration Plan

Pure restructuring of a skeleton without data or deployments: `git mv src backend/src` to keep history, then build files. Rollback is reverting the commit. No database migration exists yet.

## Open Questions

All resolved at the plan gate (user answers, 2026-11-03):

1. **SPA scope overlap with `web-shell`** - resolved: SPA fallback and the `web` -> `backend` bundle wiring stay in this change (D10, tasks 10.1 and 11.2). `web-shell` adds only the `/g/<token>` deep-link behaviour and the dev-server proxy.
2. **kotlinx.serialization scope overlap with `api-contract-foundation`** - resolved: this change installs the converter and removes `jackson-module-kotlin` (D8); `api-contract-foundation` sets the `Json` contract options.
3. **"Remove Jackson from the HTTP path"** - resolved: Jackson may stay on the classpath for Actuator endpoints only; application code stays off it (ArchUnit rule 1).
4. **Postgres version** - resolved: `postgres:18-alpine` for compose, Testcontainers and the migration plugin.
5. **JSR-354 implementation** - resolved: added in this change (`org.javamoney:moneta` 1.4.5, D4; verified by M1, M2 and B7). Still no money code here.
6. **Wasm test runtime** - resolved: `:shared` runs Wasm tests on Node (D3); `web-shell` (a later change) decides the runtime for `:web` tests.
7. **Harness preflight** - resolved: the `preflight.py` update is part of this change (D11, verified by H1); the user approved editing `.claude/hooks/preflight.py`.
8. **Package/group name** - resolved: `com.example.harnessengineeringspring` and `com.example` are kept, no rename.
