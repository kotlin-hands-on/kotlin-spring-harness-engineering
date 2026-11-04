# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Group expense-sharing application: a Kotlin Multiplatform API contract (`:shared`), a Spring Boot backend (`:backend`) with Postgres, and a Compose Multiplatform wasmJs web app (`:web`). Development is spec-driven — behavior is specified in OpenSpec before it is coded.

## Module map

| Module | What it is |
|---|---|
| `:shared` | Kotlin Multiplatform (JVM + wasmJs). kotlinx.serialization API contract types. Single source of truth for the wire format — consumed by both backend and web. |
| `:backend` | Spring Boot (WebMVC) REST API under `/api/v1`. Exposed DSL only (no DAO API), Flyway + Postgres, `javax.money`/Moneta. Packages the `:web` browser bundle into the boot jar's static resources (backend tests never build the bundle). |
| `:web` | Compose Multiplatform wasmJs browser app consuming `:shared`; publishes a `webDistribution` artifact that `:backend` consumes. |
| `build-logic/` | Included Gradle build with convention plugins (`conventions.kotlin-jvm`, `conventions.kotlin-multiplatform`, `conventions.detekt`). Build changes go here. |
| `openspec/` | Proposals and current specs — see workflow below. |
| `docs/adr/` | Architecture Decision Records. Not yet created; the directory is created with the first ADR. |

## Commands

- `./gradlew check` — full verification (tests incl. ArchUnit + detekt). The pre-commit hook runs exactly this; bypass only in emergencies with `git commit --no-verify`.
- `./gradlew :backend:test` — backend tests. **Requires Docker**: they run against a real `postgres:18-alpine` via Testcontainers.
- Single test class: `./gradlew :backend:test --tests "com.example.harnessengineeringspring.group.SomeTest"`
- `./gradlew :shared:check` — shared module tests on both JVM and wasmJs targets.
- Local run: `docker compose up -d` (Postgres; `application.properties` defaults match), then `./gradlew :backend:bootRun`.

### Database migrations

Schema changes are Flyway scripts in `backend/src/main/resources/db/migration/` — Exposed never creates DDL at runtime. Generate a migration from the Exposed tables with `./gradlew :backend:generateMigrations` (Docker required; explicit-only task, never part of `check`). Review the generated file before committing; triggers are hand-written.

## Workflow rules

### Every code change goes through OpenSpec

No code is written without an OpenSpec change. Use the OpenSpec skills (`openspec-propose` / `opsx:propose`, then `openspec-apply-change`, `openspec-update-change`, `openspec-archive-change`):

- `openspec/specs/<capability>/spec.md` — the current truth. **Read the relevant specs (and ADRs) before writing any code.**
- `openspec/changes/<change>/` — active proposals: `proposal.md`, `design.md`, spec deltas under `specs/`, `tasks.md`.
- `openspec/changes/archive/` — completed and archived changes.

The planner agent writes only below `openspec/`; the implementer agent writes code. A change is finished by syncing/archiving its spec deltas back into `openspec/specs/`.

### Architecture decisions require an ADR

- Any architecture decision MUST be documented as an ADR in `docs/adr/` and passes an approval gate: it only counts as decided once the user has explicitly approved it.
- ADRs are immutable once approved. A later decision supersedes an ADR by writing a **new** ADR; the only permitted edit to the original is adding a note that it was superseded (and by what).

### Test-driven, black-box first

- Test-driven development: write the failing test first, then the code, then refactor (see the `tdd` skill; stack idioms in `kotlin-testing` and `spring-testing`).
- Black-box and behaviour tests are heavily preferred: exercise public boundaries (HTTP API endpoints, the JSON on the wire), not internal classes. Contract tests pin the JSON exactly.
- Prefer real objects and fakes over mocks; mocking own code is an anti-pattern.

## Conventions to respect

- Money on the wire is `{"minor": <int>, "currency": "<ISO 4217>"}`; backend maps to `javax.money.MonetaryAmount`. Other API-wide rules (URL versioning, group token in path, sealed error model, `seq`/`atLeastSeq`) are specified in `openspec/specs/api-conventions/`.
- Architecture rules (layering, module boundaries) are executable ArchUnit tests in `backend/src/test/.../architecture/` — a failing `ArchitectureTest` is a design violation, not a test to relax.
- Automation is active: Claude hooks run detekt on every edited file and `check` on changed Gradle modules when work stops; report failures honestly and fix before finishing.

## Tooling rules

### Locate unknown code with jbcontext first

Before reading any file that is not already in context, find it with `jbcontext search` (semantic code search; usage reference in the managed block at the end of this file) instead of browsing directories or grepping. Once the file or symbol is identified, switch to direct reads and exact search. The managed jbcontext block is generated — do not edit it.

### Run Gradle through the Gradle MCP

The Commands section defines *what* to run — when Claude runs any of it, use the Gradle MCP tools (`gradle`, `query_build`, `wait_build`, `inspect_dependencies`, `read_dependency_sources`) instead of shelling out to `./gradlew`. Load the associated skills: `using-gradle` for running/inspecting builds, `authoring-gradle-builds` when build files or convention plugins change, `advanced-gradle-dependencies` for dependency-resolution questions, `interacting-with-project-runtime` for Kotlin REPL probes against project classes.

### Use the IDE and LSP for symbol work, not text search

The IntelliJ MCP (`mcp__idea__*`) and the Kotlin LSP plugin are available in this project. For tracking references, renaming and navigation, prefer them over grep/Read hunting:

- **References & callers**: `mcp__idea__analyze_calls` (IDE call hierarchy, incoming/outgoing) or LSP `findReferences` / `incomingCalls` — not text search.
- **Rename a variable, class or interface**: `mcp__idea__rename_refactoring` — PSI-aware, it updates references a text search cannot find. Preview wide renames with `preview=true` first; ambiguous names return a candidates list resolved with `targetIndex`. Never do multi-file renames with sed/Edit.
- **Definitions, implementations, symbol lookup**: LSP `goToDefinition` / `goToImplementation` / `workspaceSymbol`, or `mcp__idea__search_symbol`.
- **Diagnostics**: `mcp__idea__get_file_problems` — but IntelliJ can show stale unresolved references on newly created files while Gradle is green; the Gradle MCP result is the source of truth (an IDE re-sync clears it).
- **Moving files is not supported** by the IDE MCP (no move-refactoring exposed) — move with git, then update references via the rename/call tools. Other IDE tools not directly exposed can be invoked through the universal `mcp__jetbrains_air_ide__execute_tool`.

<!-- jbcontext-instructions-start -->
# Tools

## Semantic Code Search (jbcontext)

You have access to `jbcontext search` for searching the codebase semantically.
It finds code by meaning, not just keywords.

### Usage

```bash
jbcontext search "<detailed and descriptive query>"
jbcontext search -p <path> "<query>"  # <path> must be relative to the project root
```

### Query Tips

- Be descriptive: "Where is a function that validates user email addresses" > "email"
- Include context: "Find error handling middleware for HTTP requests with logging"
- Specify what you're looking for: "React component that renders a modal dialog"

### When to use

`jbcontext search` is a **search tool for repositories**. Use it when you need to find something in the
repo and don't know where to look.

Skip it - go straight to the right tool - when:

- the task names the exact file, class, or symbol (keyword grep is faster);
- the relevant file is already open or identified;
- the task doesn't involve locating code at all - git operations (rebase, merge, commit), running tests or builds,
  shell/statusline/config setup, or reviewing a diff you already have.

### MANDATORY: jbcontext-first bootstrap

When code discovery *is* needed and no relevant file or subsystem is known yet, your first code-search action MUST be
`jbcontext search` - do not open with `rg`/`grep`/`find` or git history. This mandate governs *how* you start searching,
not whether every task needs a search.

### How to use it

- Start with `jbcontext search` before planning, editing, or exact search in unfamiliar code when you do not yet know
  the right file, subsystem, implementation, or related test.
- Use one focused natural-language query per search.
- Do not start with grep, ripgrep, or find when the search problem is still semantic or exploratory.
- Inspect the first relevant file or directory before issuing another broad semantic search.
- Use another broad `jbcontext search` only if the local path stops being productive.
- Once you know the relevant file, symbol, or directory, switch to direct file reads or exact search for local
  inspection.
- If you search again after finding a relevant area, narrow with `-p <path>`.

### Examples

```bash
# Find code by its behavior
jbcontext search "code that retries failed HTTP requests with exponential backoff"

# Narrow to specific directory
jbcontext search -p src/auth "Where is the code that verifies a JWT's signature and checks that it has not expired?"
# Find related tests
jbcontext search -p test "tests checking that unauthenticated requests are rejected by middleware"

# Find documentation or specs
jbcontext search "design doc describing how the authentication flow handles token refresh"
```

<!-- jbcontext-instructions-end -->