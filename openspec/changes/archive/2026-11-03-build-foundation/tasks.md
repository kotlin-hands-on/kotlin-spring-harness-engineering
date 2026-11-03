# Tasks

Scenario IDs (B*, SH*, M*, H*, P*, J*, A*, S*) and decisions (D*) refer to `design.md`. Every task leaves `./gradlew check` green for the projects it touches. Behaviour tasks are red -> green: write the named test first, see it fail for the stated reason, then make it pass. Build tasks are red -> green on the named verification command.

## 1. Build logic and version catalog

- [x] 1.1 Add `gradle/libs.versions.toml` with every version and plugin currently hard-coded in `build.gradle.kts` (Kotlin 2.4.21, Spring Boot 4.1.2, dependency-management 1.1.7, Detekt 2.0.0-alpha.6, JUnit 6.1.3, coroutines 1.10.2) plus `jdk = "25"` (the single source for the JDK toolchain, D1/D11) and switch the existing root build to catalog accessors without changing behaviour (D1). Verify: `./gradlew check` passes with the same tests as before.
- [x] 1.2 Create the `build-logic` included build (`pluginManagement { includeBuild("build-logic") }`) with the `conventions.kotlin-base` and `conventions.kotlin-jvm` precompiled plugins carrying the current Detekt, power-assert, compiler-flag, toolchain (`jvmToolchain` from the catalog `jdk` entry) and test-logging configuration (D1, D2); apply `conventions.kotlin-jvm` in the still single-module root build and delete the duplicated blocks. Verify: `./gradlew check` passes and B2 for `src/main` and `build.gradle.kts` (deliberate violation fails `:detekt`/`:check`, revert passes).

- [x] 1.3 Update `.claude/hooks/preflight.py::required_jdk` to read `jdk = "N"` from the `[versions]` table of `gradle/libs.versions.toml` instead of `jvmToolchain(N)` in the root `build.gradle.kts`, and reword its problem message so it no longer names the literal `jvmToolchain(N)` call (D11; edit approved by the user at the plan gate, no other hook is touched). Red: run H1 first - with `jdk = "99"` in a temporary repo the unchanged hook reports nothing (it still looks at `build.gradle.kts`). Verify: H1 (all three cases, plus a run on this repo exits 0 with no JDK problem).

## 2. Backend module

- [x] 2.1 Move the application into `:backend` (`git mv src backend/src`), add `backend/build.gradle.kts` applying `conventions.kotlin-jvm`, `plugin.spring`, Spring Boot and dependency-management with today's dependencies, `include(":backend")` in settings; reduce the root build to Detekt over `settings.gradle.kts` and the root `build.gradle.kts` (D1, D3). Verify: `./gradlew :backend:check` passes (existing `JUnitSetupTest` and `contextLoads`), `./gradlew projects` lists `:backend`, B2 for `settings.gradle.kts` via root `:detekt`.

## 3. Shared module

- [x] 3.1 Add the `conventions.kotlin-multiplatform` plugin with platform-neutral flags on all compilations and `-Xjsr305=strict` on JVM only (D1), and create `:shared` with `jvm()`, `wasmJs { nodejs() }`, the serialization plugin and `kotlinx-serialization-json` in `commonMain` (D3); JVM tests use JUnit 6 + `kotlin("test")`. Red: `SerializationSetupTest` in `commonTest` covering SH1 and SH2 fails to compile without the plugin/flags. Verify: `./gradlew :shared:jvmTest :shared:wasmJsTest` pass, B2 for `src/commonMain/kotlin` and `shared/build.gradle.kts`.
- [x] 3.2 Add `implementation(project(":shared"))` to `:backend` (D3). Verify: `inspect_dependencies` shows `:shared` (jvm variant) on `:backend:runtimeClasspath` and `./gradlew :backend:check` passes.

## 4. Shared Spring test context and Postgres

- [x] 4.1 Add Postgres driver (runtime), `spring-boot-testcontainers`, `testcontainers-postgresql`, the `IntegrationTest` meta-annotation and `TestcontainersConfig` with a `@ServiceConnection` `postgres:18-alpine` container (D6), and datasource properties with env overrides and compose defaults (D4). Red: `PersistenceSmokeTest` (`@IntegrationTest`) asserting P1 via MockMvc fails before the wiring. Replace `HarnessEngineeringSpringApplicationTests.contextLoads` with it. Verify: P1 passes, `./gradlew :backend:check` passes.

## 5. Flyway

- [x] 5.1 Add `spring-boot-starter-flyway` and `flyway-database-postgresql`, create `backend/src/main/resources/db/migration/.gitkeep` (D4). Red: P2 added to `PersistenceSmokeTest` fails without a `Flyway` bean. Verify: P2 passes.

## 6. Exposed

- [x] 6.1 Add `exposed-spring-boot4-starter`, `exposed-core`, `exposed-jdbc`, `exposed-json`, `exposed-money` at one catalog version 1.6.0, `@SpringBootApplication(exclude = [DataSourceTransactionManagerAutoConfiguration::class])` with no explicit Exposed import (the starter registers its auto-configuration itself), and `spring.exposed.generate-ddl=false` (D4). Red: P3 added to `PersistenceSmokeTest` fails (no Exposed transaction manager). Verify: P3 passes, B7 for Exposed versions.
- [x] 6.2 Apply `org.jetbrains.exposed.plugin` 1.6.0 in `:backend` with the D5 configuration and document the explicit generation command (`./gradlew :backend:generateMigrations`, Docker required, review before commit) in `HELP.md`. Verify: B3 (`generateMigrations` absent from `./gradlew build --dry-run`; explicit run with Docker completes and writes nothing - if it fails only because no tables exist, record the exact message in the task and stop there).

- [x] 6.3 Add `org.javamoney:moneta` 1.4.5 (catalog version `moneta`) as `implementation` in `:backend` (D4). Red: `MoneyProviderTest` (plain JUnit, no Spring context) covering M1 and M2 fails with `MonetaryException` (no provider). Verify: M1, M2 pass; B7 for `moneta`/`money-api` resolution on `:backend:runtimeClasspath` (add a catalog constraint only if versions diverge).

## 7. Local dev environment

- [x] 7.1 Add `compose.yaml` at the repo root per D7 (same `postgres:18-alpine` tag as 4.1 and 6.2) and a "Local development" section in `HELP.md` (`docker compose up -d`, `./gradlew :backend:bootRun`, env overrides `DB_URL`/`DB_USER`/`DB_PASSWORD`). Verify: B6 exactly as documented.

## 8. kotlinx.serialization on the HTTP path

- [x] 8.1 Add a test-only probe controller under `/api/__probe/json` in `backend/src/test/kotlin` (picked up by the shared context) and `JsonSerializationTest` (`@IntegrationTest`, MockMvc) for J1 and J2. Red: J1 fails because Jackson emits the Kotlin property name. Green: apply the serialization plugin to `:backend`, add `spring-boot-starter-kotlinx-serialization-json`, remove `jackson-module-kotlin`; `spring.http.converters.preferred-json-mapper` is not set because Boot 4.1.2 has no kotlinx value and J1/J2 guard the converter selection (D8). Verify: J1, J2 pass; P1 still passes (Actuator JSON); B7 for `kotlinx-serialization-json` alignment and absence of `jackson-module-kotlin`.

## 9. Architecture test

- [x] 9.1 Add `archunit-junit6` 1.5.1, `archunit.properties` and `ArchitectureTest` with the three D9 rules (snake_case names, `.because(...)`). Verify: A1 - the test passes, and each rule was shown to fail once on a temporary violation in production code (Jackson import, Exposed DAO import, field injection), then reverted.

## 10. SPA serving

- [x] 10.1 Add fixtures `backend/src/test/resources/static/index.html` (marker string) and `static/probe.wasm`, and `SpaFallbackTest` (`@IntegrationTest`, MockMvc) for S1-S6. Red: S2 fails (no fallback). Green: the D10 `WebMvcConfigurer` with the index fallback rules (and a `wasm` MIME mapping only if S3 fails). Verify: S1-S6 pass, A1 still passes.

## 11. Web module and bundle wiring

- [x] 11.1 Create `:web` with `conventions.kotlin-multiplatform`, `wasmJs { browser(); binaries.executable() }`, the Compose Multiplatform and Compose compiler plugins, a dependency on `:shared`, a placeholder `ComposeViewport` entry point (no `!!`) and `index.html` (D3); no yarn lock is committed while `:web` has no npm dependencies (D3; the lock under `kotlin-js-store/` is committed once the first npm dependency appears in `web-shell`). Verify: B4, B1, and B2 for `src/wasmJsMain/kotlin` and `web/build.gradle.kts`; `./gradlew :web:check` passes without a browser.
- [x] 11.2 Expose the `:web` distribution through a consumable configuration and add it to `:backend` `bootJar` under `BOOT-INF/classes/static/` without touching `processResources` (D10). Verify: B5 (jar contents listed with `unzip -l backend/build/libs/*.jar`; `:backend:test --dry-run` lists no `:web:` task).

## 12. Integration check

- [x] 12.1 Run the full build from a clean state and the cross-module checks. Verify: `./gradlew clean build` passes; B7 complete (Jupiter/Platform 6.x on every test runtime classpath, Exposed 1.6.0, aligned `kotlinx-serialization-json`, no `jackson-module-kotlin`, Moneta 1.4.5 with a consistent `money-api`); H1 once more against the final tree; the Postgres tag is identical in `compose.yaml`, `TestcontainersConfig` and the Exposed plugin configuration.

## Workflow follow-up

- Open questions were resolved at the plan gate (design.md "Open Questions"): SPA/JSON splits confirmed, Moneta added (6.3), the preflight hook update added (1.3).
- Run `change-review`, then archive the change once review requirements are satisfied.
