# Design

## Context

See proposal.md - Why / What Changes. Observed current state (after `build-foundation`, archived 2026-11-03):

- Three modules: `:shared` (KMP `jvm()` + `wasmJs { nodejs() }`, `kotlinx-serialization-json` in `commonMain`, tests run on both targets, package root `com.example.harnessengineeringspring.shared`), `:backend` (Spring Boot 4.1.2, package root `com.example.harnessengineeringspring`), `:web` (Compose/Wasm stub, untouched by this change).
- Backend HTTP path already uses kotlinx.serialization (`spring-boot-starter-kotlinx-serialization-json`, `jackson-module-kotlin` removed); the contract-level `Json` settings were explicitly deferred from `build-foundation` D8 to this change.
- `org.javamoney:moneta` 1.4.5 is on the backend classpath (verified by `MoneyProviderTest`), so `Monetary.*` resolves; no money code exists yet.
- Test infrastructure: one shared `@IntegrationTest` context (Testcontainers Postgres + MockMvc), test-only probe controllers under `/api/__probe/*` in test sources (`JsonProbeController`), ArchUnit 1.5 with `ArchitectureTest` + `archunit.properties`, Detekt on every module.
- SPA fallback serves HTML for extension-less unknown paths but explicitly excludes `/api/` and `/actuator/`; a request to an unknown `/api/...` path currently returns the servlet default 404.
- No main specs exist yet; this change is the first. No production code exists in `:shared`.
- Downstream consumers of these conventions (all proposal-only stubs): `groups-and-members` (group DTO with currency + fraction digits, token issuance), `ledger-core` (`seq`, `atLeastSeq` semantics, payload types), `expenses-equal-split` (`expectedRevision` -> `conflict`, `SplitIntent` sealed type), `web-shell` (client-side handling of seq and sealed errors).

Project policy that binds this design: sealed results for domain errors, no `!!`, minimum visibility (`kotlin-engineer`); one shared Spring test context, fakes over mocks, black-box tests through public boundaries (`spring-testing`, `tdd`).

## Goals / Non-Goals

**Goals:**

- One canonical contract surface in `:shared` that both compile targets and the backend use verbatim: the `Json` configuration, `MoneyDto`, the sealed `ApiError` model, and the naming conventions (`/api/v1`, token-in-path, `seq`, `atLeastSeq`).
- The error model rendered on HTTP in exactly one place (status mapping + envelope), already wired so later changes only raise errors, never re-implement rendering.
- Access tokens impossible to leak through request logs.
- The wire format pinned by exact-JSON contract tests on JVM and Wasm, so any breaking change fails the build.
- The DTO/payload separation made enforceable now, before ledger payload types exist.

**Non-Goals:**

- No real `/api/v1` endpoints, no group/member/expense/ledger types, no token generation or validation (all later changes).
- No `atLeastSeq` semantics (long-poll, staleness flag) - owned by `ledger-core`; this change fixes names only.
- No client code (`web-shell`).
- No OpenAPI generation, no pagination conventions, no auth beyond the token-in-path shape.
- No changes to `:web`, to build logic, or to `.claude/` harness files.

## Decisions

### D1 - Contract types live in `com.example.harnessengineeringspring.shared.api`

All wire DTOs introduced now and later (`MoneyDto`, `ApiError`, `ErrorResponse`, group/expense DTOs in later changes) go in a dedicated `shared.api` package of `:shared`, not in the `shared` package root. The API/ledger separation (see D7) needs the package boundary as its anchor, and the root stays free for non-wire utilities. Kotlin-engineer policy: DTOs stay dumb data holders (immutable, no behaviour); they are not value classes (they must serialize polymorphically and grow fields).

### D2 - One `Json` configuration, owned by `shared`

`shared.api.ApiJson` exposes a single source of truth for the contract settings as a `JsonBuilder.() -> Unit` extension (plus a ready `Json` built from it):

- `ignoreUnknownKeys = true` - requests with fields the server does not know are accepted (additive evolution within v1).
- `encodeDefaults = false` and `explicitNulls = false` - absent means default; optional fields can be added later without breaking pinned JSON.
- discriminator left at the kotlinx default `"type"` (pinned by contract tests, so it cannot drift silently).
- `isLenient = false`, `coerceInputValues = false` - structure violations are validation failures, not silent fixes.

The backend applies the same builder lambda to its HTTP `Json` (a `KotlinxSerializationJsonBuilderCustomizer`-style bean if Boot 4.1 exposes one, otherwise a customizer bean / `@Bean Json` that backs the auto-configuration off - the probe parity test, not the wiring mechanism, is the acceptance criterion). The shared contract tests and the HTTP layer therefore cannot diverge. This resolves the item `build-foundation` D8 explicitly left open.

Why one source: two configured `Json` instances (test-side and server-side) is the classic drift that contract tests are supposed to catch but cannot, because each side pins its own instance. Alternatives: server settings in `application.properties` (a second copy to keep in sync, invisible to Wasm tests); strict unknown-key rejection (breaks additive evolution).

### D3 - Money: minimal wire type in `shared`, mapping in `backend`

- `shared`: `@Serializable data class MoneyDto(val minor: Long, val currency: String)` - nothing else; no validation logic, no ISO table (Wasm targets have no `javax.money`). Contract tests pin `{"minor":1234,"currency":"EUR"}` exactly.
- `backend`: a pure mapper `MoneyDto -> MonetaryAmount` and back, in the existing `...money` package. Scaling uses the currency's **default fraction digits** from `Monetary.getCurrency(code).defaultFractionDigits` (BigDecimal moved by that many places), so JPY (0 digits) maps identity-exact and EUR (2 digits) maps 1234 -> 12.34.
- Unknown currency codes: mapping returns a **sealed result** (`Mapped(amount)` / `UnknownCurrency(code)`), never throws for a domain condition (kotlin-engineer). Callers translate `UnknownCurrency` into `validation_failed`; the currency-vs-group-currency check (`currency_mismatch`) needs group context and belongs to `groups-and-members`.
- Reverse mapping (`MonetaryAmount -> MoneyDto`) is also provided: later features serialize computed amounts (allocations, balances).

### D4 - Sealed error model: shared DTO, one rendering point, fixed status map

- `shared.api.ApiError`: sealed interface, subtypes with stable `@SerialName` codes: `ValidationFailed`, `NotFound`, `Conflict`, `CurrencyMismatch`. Common `message: String`; subtype extras (e.g. `Conflict`'s `expectedRevision` later) use defaulted properties so `encodeDefaults=false` omits them today. Envelope: `@Serializable data class ErrorResponse(val error: ApiError)` -> `{"error":{"type":"not_found","message":"..."}}`.
- `backend`: controllers/services signal rejections with domain sealed results (policy); the HTTP adapter converts them to `ApiException(val error: ApiError)` (a plain `RuntimeException` - exceptions are used only at the HTTP boundary, not for domain control flow). `ApiErrorAdvice` (`@RestControllerAdvice`) is the single place that maps `ApiError -> HTTP status` and renders the envelope:

  | code | status |
  |---|---|
  | `validation_failed` | 400 |
  | `not_found` | 404 |
  | `conflict` | 409 |
  | `currency_mismatch` | 422 |

- `ApiErrorAdvice` also handles the container's "no handler" exception for paths under `/api/` and renders it as `not_found`, so an unknown API route returns the JSON envelope instead of the servlet default page (the SPA fallback already keeps HTML out of `/api/`). Statuses outside the four (401/403/500) are future work; nothing in scope emits them.
- Message text is human-oriented, never parsed, never pinned by tests (only `type` and the envelope shape are contract). See Open Questions for the 422 choice.

### D5 - Consistency convention: names only, no semantics

This change pins the names and where they appear: every write response body contains `seq` (integer), every read endpoint accepts `atLeastSeq` (query param) and its response reports the `seq` it reflects. `ledger-core` owns waiting/staleness. Nothing is implemented here; a test-only probe pins the names so later changes cannot rename them by accident (task 5.1). Shared does not define a generic `SeqEnvelope<T>` - real write/read DTOs belong to their own features; premature generics would be pinned by contract tests and become a straitjacket.

### D6 - Token redaction: pure function + one filter

- A pure function `redact(target: String): String` (backend, `web` package) is the only place that knows token locations: the first path segment after `/groups/`, and query parameters named `token` / `accessToken`. Replaced with the fixed placeholder `[REDACTED]`. Location-based, not value-based: redaction must not depend on knowing the token value (logs are also written for requests with invalid tokens).
- A servlet filter (registered for `/api/*`, highest precedence before anything else logs) emits one INFO line per request: method, redacted target, resulting status. When the chain throws, the most specific cause of the exception is logged in the status's place and the exception is rethrown untouched, so the container's error handling still runs — the line is emitted on every path. This is the backend's request log; without it the redaction requirement has nothing to bite on.
- Redaction is unit-tested as the pure function (all path/query shapes); one integration test captures the emitted line via a Logback list appender and asserts the token value is absent while the request is served normally.

### D7 - DTO/payload separation: package convention + ArchUnit with `allowEmptyShould`

The separation is structural: wire DTOs in `shared` package `..shared.api..`; ledger event payloads (later, `ledger-core`) in backend package `..ledger..payload..`; same JSON shapes allowed, shared types forbidden. The enforceable rule today:

```
noClasses().that().resideInAnyPackage("..ledger..", "..persistence..")
    .should().dependOnClassesThat().resideInAPackage("..shared.api..")
```

with `allowEmptyShould(true)` - no ledger/persistence classes exist yet, and the per-rule flag (not the global `archunit.failOnEmptyShould`) keeps the existing rules' empty-should protection intact. The rule is proven to bite once by temporarily adding a violating production class, then reverting (same verify-then-revert pattern as `build-foundation` A1). The reverse direction (controllers never exposing payload types) is added with `ledger-core`, when the first code it guards exists (`archunit` skill: add each rule with the first code it guards).

### D8 - Probes stay test-only, outside the versioned surface

All behavior of this change that needs an HTTP endpoint to be observable (envelope rendering, status map, `seq`/`atLeastSeq` names, JSON parity) is exercised through test-only probe controllers under `/api/__probe/*` in test sources - extending the existing `JsonProbeController` convention. The `__` marker keeps them out of `/api/v1` so no production route ever exists under the versioned prefix in this change. Downstream changes replace probes with real endpoints.

## Test boundaries

Referenced by `tasks.md`; each names the boundary and what it catches / misses.

**[shared `commonTest`, run as `:shared:jvmTest` + `:shared:wasmJsNodeTest`]** - exact-JSON pins, round-trips, discriminators, `ApiJson` settings. Catches wire-format drift on both compile targets; misses all HTTP wiring.

**[backend MockMvc through the shared `@IntegrationTest` context]** - JSON parity over HTTP (unknown keys, omitted defaults), error envelope + status map, unknown `/api` route -> `not_found`, `seq`/`atLeastSeq` naming, redacted log line. Catches end-to-end HTTP behaviour incl. the converter; misses the real browser client.

**[backend plain JUnit, no Spring context]** - the money mapper. Catches fraction-digit scaling, unknown-currency rejection, reverse mapping; misses HTTP and persistence (later changes).

**[backend filter unit tests + one Logback list-appender integration test]** - token redaction. Catches the pure function over path/query shapes and the emitted log line; misses log sinks that do not exist (no other component logs request targets).

**[ArchUnit on `:backend` production bytecode]** - the DTO/payload separation rule (D7). Catches structural coupling once ledger code exists; catches nothing behavioural.

## Risks / Trade-offs

- [Boot 4.1's kotlinx `Json` customization hook may not be the assumed customizer shape] -> the parity probe test is the acceptance criterion; implementation picks whichever hook (customizer bean or user `Json` bean) achieves it and records it in the task.
- [The D7 rule passes vacuously until `ledger-core` lands] -> documented; task 7.1 proves it bites via a temporary violation; `ledger-core` re-verifies with real payload types and adds the reverse rule.
- [`encodeDefaults=false` + `explicitNulls=false` means clients must read absence as "default"] -> exactly what the contract tests pin; later optional fields must ship with defaults, which the spec's forward-compatibility requirement makes normative.
- [Redaction is location-based; a future token-carrying location could leak] -> the pure function is the single extension point; any new location must extend it (D6). No value-based scanning is attempted (unreliable).
- [422 for `currency_mismatch` is a judgement call] -> codes, not statuses, are the stable contract (see Open Questions); revisit is cheap while no client exists.
- [INFO request logging adds a line per request] -> the app currently logs nothing per request; one line is the minimum the redaction requirement can attach to.
- [Probe controllers under `/api/__probe` live only in test bytecode] -> intentional (D8); nothing pins their absence in production, but they are never deployed.

## Migration Plan

Pure additions (`shared` package + DTOs, backend advice/filter/mapper, tests); no schema, no endpoints, no data. Rollback is reverting the commit. No main specs exist yet, so archive creates the first one (`openspec/specs/api-conventions/spec.md`).

## Open Questions

Answered at the plan-approval gate; the artifacts above assume the recommended default for each. If a gate decision differs, update D3/D4/D6 and the matching spec scenarios/tasks before apply.

1. **HTTP status for `currency_mismatch`** - recommended default: **422** (semantically "well-formed but conflicting with group state", distinct from 400 input-shape failures and 409 optimistic-concurrency conflicts). Alternatives: 400 (groups-and-members proposal says "rejected with a `CurrencyMismatch` result", silent on status) or 409. Affects: spec scenario `Currency mismatch`, D4 table, task 4.1.
2. **Shape of the currency metadata in group payloads** - recommended default: **flattened sibling fields** `currency: String` + `fractionDigits: Int` on any group payload (no nested object), simplest for the client. Alternative: a nested `currencyInfo` object reusable per payload. Affects: the spec requirement "Group payloads carry currency fraction digits" as realized by `groups-and-members` (this change only pins the requirement).
3. **`ignoreUnknownKeys`** - recommended default: **true** (additive evolution within v1). Alternative: strict mode, which fails clients that send fields the server has not shipped yet. Affects: D2, spec "Forward-compatible JSON", tasks 1.1/2.1.
4. **Error envelope and discriminator** - recommended default: nested `{"error":{"type":...}}` with discriminator key **`"type"`** (kotlinx default, one less deviation to document). Alternatives: flat top-level `{"code":...}`, or a `"kind"`/`"error"` discriminator key. Affects: D4, contract tests, all downstream DTOs.
5. **Unknown `/api` routes** - recommended default: **render as the `not_found` envelope** (consistent JSON API surface; the SPA fallback already excludes `/api/`). Alternative: keep the servlet default 404 page. Affects: spec scenario "Unmatched API route", D4, task 4.1.
6. **Request logging** - recommended default: **add the INFO one-line-per-request filter now** (D6) so the redaction requirement is testable and real. Alternative: only the pure function + filter without enabling INFO (requirement untestable end to end). Affects: D6, task 6.2.
7. **Error code spelling** - recommended default: **lowercase snake_case** serial names (`validation_failed`, ...) matching the JSON field style. Alternative: `SCREAMING_SNAKE_CASE`. Affects: D4, contract tests, every downstream feature that raises errors.
