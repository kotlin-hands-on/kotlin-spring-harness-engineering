# Proposal

## Why

The repo is a single-module Spring Boot skeleton. The bill-splitting app needs a shared API contract that compiles for both the JVM backend and a Compose Wasm frontend, a Postgres-backed persistence stack, and a reproducible dev environment. Every later change depends on this structure, so it must exist first.

## What Changes

- Split the build into modules: `shared` (Kotlin Multiplatform: `jvm` + `wasmJs`), `backend` (Spring Boot), `web` (Compose for Web, Wasm).
- Add `docker-compose` with Postgres for local development.
- Add persistence stack: Exposed (DSL, JDBC, `exposed-money`, `exposed-json`), Flyway, and the Exposed Gradle migration plugin (Testcontainers baseline; generation is an explicit task, never part of `build`).
- Adopt kotlinx.serialization as the wire serializer; remove Jackson from the HTTP path.
- Add Testcontainers (`@ServiceConnection`) for Postgres-backed tests, and ArchUnit with an initial architecture test.
- Keep existing Detekt, power-assert and Kotlin compiler-flag setup, applied to every module.
- Spring Boot serves the built Wasm bundle with SPA fallback (wiring only; no UI yet).

## Capabilities

### New Capabilities
<!-- None: tooling and project structure only. The change sets skip_specs: true. -->

### Modified Capabilities

## Impact

- Gradle: `settings.gradle.kts`, per-module build files, version catalog/conventions.
- New directories: `shared/`, `backend/`, `web/`, compose file, `db/migration` resources.
- Existing `src/` moves into `backend/`.
- Requires Docker for migration generation and persistence tests.
- Out of scope: any domain code, API endpoints, UI screens, CI pipeline.
