# Design

## Context

See proposal.md - Why / What Changes. Observed current state (after `api-contract-foundation`, archived 2026-11-03):

- `:shared` (`commonMain`, package root `...harnessengineeringspring.shared`) already has `shared.api`: `ApiJson` (one contract `Json`: `ignoreUnknownKeys=true`, `encodeDefaults=false`, `explicitNulls=false`, default `"type"` discriminator), `MoneyDto(minor, currency)`, sealed `ApiError` (`validation_failed`/`not_found`/`conflict`/`currency_mismatch`), `ErrorResponse`. Every DTO must get exact-JSON contract tests on JVM and Wasm (api-conventions).
- `:backend` already has the single rendering point `ApiErrorAdvice` + `ApiException` (status map 400/404/409/422), the request-log filter with the pure `redact` function (first path segment after `/groups/`, `token`/`accessToken` query params -> `[REDACTED]`), the money mapper `MoneyMapping` (`MoneyDto -> MonetaryAmount` sealed result, `UnknownCurrency` for unresolvable codes), and one shared `@IntegrationTest` context (Testcontainers Postgres, MockMvc). Exposed (DSL only, no DAO), Flyway, `exposed-money` and Moneta are on the classpath; the Exposed Gradle plugin is configured in `backend/build.gradle.kts` with `tablesPackage = com.example.harnessengineeringspring.persistence` and output to `src/main/resources/db/migration` (empty today - no migration exists yet).
- `PersistenceSmokeTest` pins that all migrations are applied and Exposed runs on the Spring transaction manager. `ArchitectureTest` already forbids `..ledger..`/`..persistence..` depending on `..shared.api..` and forbids the Exposed DAO API.
- No group, member, ledger or expense types exist anywhere. All money capabilities (`ledger-core`, expenses, balances, ...) are proposal-only stubs; nothing in this change may implement or pre-empt them.

Project policy that binds this design: sealed results for domain errors, no `!!`, value classes, minimum visibility (`kotlin-engineer`); one shared Spring test context, fakes over mocks, black-box tests through public boundaries (`spring-testing`, `tdd`); vertical slices (each task lands shared DTO + endpoint + persistence + tests together).

## Goals / Non-Goals

**Goals:**

- The first real `/api/v1` endpoints: group create/read/rename and member add/list/update/delete, each addressed by the secret token in the path per api-conventions.
- A token that is genuinely unguessable, returned once, stored hashed, and never in a log line (existing redaction applies to the new routes unchanged).
- The group-currency boundary (the future `currency_mismatch` gate for every money endpoint) implemented once, in one place, so later expense changes call it instead of re-implementing it.
- The member deletion contract (hard delete only while unreferenced, `ON DELETE RESTRICT` -> `conflict`) in place before the ledger exists, so `ledger-core` only adds the referencing FK.
- Two generated Flyway migrations (V1 groups, V2 members) produced by the already-configured Exposed plugin.

**Non-Goals:**

- No ledger, expenses, balances, settlements, feed, or any event sourcing for groups/members (plain CRUD tables; their changes are never ledger events - `ledger-core` owns that world).
- No accounts, no member claim ("I am this member"), no invite mechanism beyond sharing the link.
- No group deletion endpoint, no token rotation/recovery (a lost link is a lost group).
- No UI (`:web` untouched), no OpenAPI, no pagination, no `atLeastSeq` semantics (names only, per api-conventions; semantics owned by `ledger-core`).
- No monetary columns in this change's schema: no table here stores an amount (the group stores a currency *code*); `exposed-money` column types arrive with the first amount-carrying table (`ledger-core`).

## Decisions

### D1 - Package layout: feature packages over layers, tables in `persistence`

Controllers and services live in feature packages `...harnessengineeringspring.group` and `...member`; Exposed tables in the already-configured `...persistence`; HTTP-shared machinery (`ApiErrorAdvice`, filters) stays in `...web`. Each feature package is internally layered service -> controller, with controllers never touching `persistence` (see D8). Alternative: an `api`/`service`/`persistence` layer per the whole app - rejected because the app's existing structure is feature-agnostic web plumbing plus feature packages would then have no home.

### D2 - Token: 256 bits of `SecureRandom`, URL-safe, stored as SHA-256

- Generation: 32 random bytes from `java.security.SecureRandom`, URL-safe Base64 without padding (43 chars, no `/` or `+`, safe in a path segment) - no new dependency.
- At rest: the `groups` table stores `token_hash` (SHA-256 hex of the token), not the token; lookups hash the incoming token and search a unique index. A high-entropy token needs no slow password hash; plain SHA-256 makes the credential non-recoverable from a database dump while keeping lookup an index hit. Alternative: storing the raw token (simpler, reversible on dump) - rejected as the token *is* the only credential.
- Wire: returned exactly once, in the creation response (`GroupResponse.token`, defaulted so `encodeDefaults=false` omits it on every read - pinned by contract tests).

### D3 - DTOs in `shared.api`, ids and seq always encoded

Per api-conventions every group payload carries `currency` + `fractionDigits` (flattened sibling fields - the archived change's open question 2, resolved at its gate). DTOs (all in `shared.api`, dumb data holders):

- `CreateGroupRequest(name: String, currency: String)`
- `GroupResponse(id: String, name: String, currency: String, fractionDigits: Int, token: String? = null, seq: Long)` - `token` defaulted (omitted on reads, present on create); `seq` NOT defaulted so `encodeDefaults=false` still emits it (a defaulted `seq = 0` would be *omitted* from every write response and break the api-conventions "Write response carries seq" scenario - the field must have no default).
- `UpdateGroupRequest(name: String)`
- `AddMemberRequest(name: String)`
- `UpdateMemberRequest(name: String? = null, active: Boolean? = null)` - PATCH semantics: absent = unchanged (nulls omitted from JSON via `explicitNulls=false`).
- `MemberResponse(id: String, name: String, active: Boolean, seq: Long)` - `active` and `seq` not defaulted, always emitted.
- `MemberListResponse(members: List<MemberResponse>, seq: Long)`.

Backend-side value classes (`kotlin-engineer`): `GroupToken`, `GroupId`, `MemberId` wrap the wire strings/UUIDs at the service boundary so ids cannot be mixed up; DTO fields stay `String`/UUID-string on the wire.

### D4 - Sealed results end at the one rendering point

Services return sealed results for every domain rejection (`GroupNotFound(token)`, `MemberNotFound`, `InvalidName`, `UnknownCurrency(code)`, `CurrencyMismatch(expected, actual)`, ...); controllers are the only place that maps a result to `ApiException(ApiError.X)`, which `ApiErrorAdvice` renders. No domain exceptions, no `!!`. The status map itself already exists and is not re-opened.

### D5 - Currency: resolve once, validate at the boundary, never store digits

- Creation resolves the code via `Monetary.getCurrency` (a small pure resolver in the backend `money` package, sealed result - reusing the `MoneyMapping` `UnknownCurrency` idea for a bare code) and stores only the code; `fractionDigits` is computed at read time from `Monetary`, never stored (names resolved at read time, likewise digits).
- The group-currency gate is one service, `GroupAccess`: given a token and an optional `MoneyDto`, it resolves the group (sealed `NotFound`) and validates the amount (reuse `MoneyMapping.toMonetaryAmount`: `UnknownCurrency` -> `validation_failed`, mapped currency != group currency -> `CurrencyMismatch`). Every future amount-carrying endpoint calls this gate; it is exercised now through a test-only probe under `/api/__probe/*` (the established probe convention), to be replaced by real endpoints in the expense changes.
- Rename accepts only `name` (D3); a `currency` field in a rename body is an unknown field, ignored per the tolerant decoding convention - the spec pins "currency unchanged", not an explicit rejection (open question 1).

### D6 - Persistence: two tables, two generated migrations, delete guard via FK

- `Groups(id uuid pk, name, currency, token_hash unique)` - V1, generated by the Exposed plugin, lands with the group slice.
- `Members(id uuid pk, group_id uuid -> groups.id, name, active boolean)` - V2, generated likewise, lands with the member slice. No unique constraint on `(group_id, name)` (open question 3). Member list is ordered by `name, id` for determinism.
- The `ON DELETE RESTRICT` FK from `ledger_event.actor_member_id` belongs to `ledger-core` (it owns that table); this change makes the contract ready: the delete path translates a database referential-integrity violation into `conflict` (409). To keep that translation black-box tested *now*, the delete test creates a test-scoped stand-in table with an `ON DELETE RESTRICT` FK to `members(id)` (DDL is transactional in Postgres; the test commits the reference row, asserts the 409, cleans up) - unique table name, created and dropped by the test itself, no overlap with any future `ledger-core` migration.
- Group and member rows are plain CRUD: no event rows are written, no projector touches them.

### D7 - `seq` and `atLeastSeq`: names per convention, constant 0 for now

Groups/members are not ledger events, so their endpoints report the group's *current* ledger head, which is a constant `0` until `ledger-core` wires the real head. Writes return `seq: 0` in the body; reads accept `atLeastSeq` (accepted, no semantics) and report `seq: 0`. This keeps the api-conventions naming scenarios green without implementing (or pre-empting) semantics owned by `ledger-core` (open question 2).

### D8 - ArchUnit: controllers stay off persistence

New rule alongside the existing ones: no class annotated `@RestController` may depend on `..persistence..` or any `org.jetbrains.exposed` package - controllers talk to services and `shared.api` DTOs only. Added with the first code it guards, proven to bite once by verify-then-revert (same pattern as the archived change's task 7.1). The reverse concern (services depending on DTOs) is fine - services speak DTOs and value classes.

## Test boundaries

Referenced by `tasks.md`; each names the boundary and what it catches / misses.

**[shared `commonTest`, run as `:shared:jvmTest` + `:shared:wasmJsNodeTest`]** - exact-JSON pins and round-trips for every new DTO: creation shape (token present), read shape (token absent), `active`/`seq` always present even at their zero values, PATCH-request null-omission. Catches wire-format drift on both targets; misses all HTTP wiring and persistence.

**[backend MockMvc through the shared `@IntegrationTest` context]** - the primary boundary for every endpoint behaviour: creation/read/rename, member lifecycle, scoping (cross-group id -> 404), delete + the FK-stand-in `conflict`, list order, currency gate via the probe, error envelopes and statuses, and the redacted log line for a real `/api/v1/groups/<token>/...` route. Real Flyway migrations on real Postgres, so each slice also proves its migration applies. Misses the browser client and any ledger behaviour (none exists).

**[backend plain JUnit, no Spring context]** - the token generator (length, URL-safe charset, sample-uniqueness, non-predictability between two instances), SHA-256 hashing determinism, the currency-code resolver (fraction digits, unknown code). Catches pure-logic regressions cheaply; misses HTTP and persistence.

**[ArchUnit on `:backend` production bytecode]** - the controller/persistence separation (D8). Catches structural coupling; catches nothing behavioural.

**Explicitly not tested:** brute-force resistance of tokens (entropy is asserted by construction, not by attack), database performance, the `web` client, `atLeastSeq` waiting/staleness semantics (`ledger-core`), ledger/activity-feed integration (no ledger exists; the referenced-member scenario's real FK arrives with `ledger-core`), and the exposed-money column mapping (no monetary column exists in this change).

## Risks / Trade-offs

- [The referenced-member 409 is tested against a stand-in FK, not the real `ledger_event` FK] -> the stand-in exercises exactly the same DB rejection path (`ON DELETE RESTRICT` -> constraint violation -> mapped to `ApiError.Conflict`); `ledger-core` re-verifies end-to-end when its migration adds the real FK.
- [Constant `seq: 0` could be mistaken for real consistency] -> api-conventions owns only the naming; the design states the value is the group's ledger head (currently 0); `ledger-core`'s change owns wiring it. Tests assert presence/type, never the value's meaning.
- [`GroupResponse.token` being a defaulted optional field means a read payload silently omits it] -> exactly what the contract tests pin; a client never needs the token echoed (it just used it in the path).
- [SHA-256-at-rest means the token cannot be recovered from the database] -> intended (lost link = lost group); a future token-rotation feature would issue a fresh token, not recover the old one.
- [No `(group_id, name)` uniqueness allows two members named "Alice"] -> plain-CRUD default; open question 3 records the alternative (one unique index + one rejection scenario) for the gate.
- [Members migration (V2) is generated mid-change rather than with V1] -> keeps the vertical-slice rule (each slice owns its persistence); Flyway orders by version, so applying V1 then V2 is exactly what production will do.
- [Probe endpoint exercises the currency gate until real endpoints exist] -> same convention the archived change used (`/api/__probe` test-only); the expense changes replace the probe with real amount-carrying endpoints calling the same `GroupAccess` service.

## Migration Plan

Pure additions: two Flyway migrations generated by the Exposed plugin (V1 `groups`, V2 `members`), new packages, new endpoints; no existing behaviour changes. Deploy: standard boot startup applies pending migrations in order. Rollback in development: revert the commit and drop the schema (`flyway clean` equivalent via compose volume reset); no data exists that predates the change. Archive creates the main specs `openspec/specs/group-access/` and `openspec/specs/group-members/`.

## Open Questions

Answered at the plan-approval gate; the artifacts above assume the recommended default for each. If a gate decision differs, update the matching spec scenario(s), design decision and tasks before apply.

1. **Rename request carrying a `currency` field** - recommended default: **ignored** as an unknown field (consistent with the pinned tolerant decoding; currency can never change). Alternative: declare the field in `UpdateGroupRequest` and explicitly reject a differing value with `validation_failed`. Affects: `group-access` scenario "A rename request carrying a currency field does not change it", D5, rename task.
2. **`seq` value on group/member endpoints before the ledger exists** - recommended default: **constant 0** (the group's ledger head while no ledger exists; names honoured, semantics deferred to `ledger-core`). Alternatives: omit `seq` until `ledger-core` (violates the api-conventions naming requirement) or introduce a per-group counter now (pre-empts `ledger-core`). Affects: D3 (non-defaulted `seq`), D7, the DTO contract tests, every endpoint task.
3. **Duplicate member names within a group** - recommended default: **allowed** (plain CRUD, no uniqueness; ledger resolves members by id, never name). Alternative: unique `(group_id, name)` index rejected with `conflict` (409). Affects: D6, the members migration task, and would add a `group-members` rejection scenario.
4. **Reactivating a deactivated member** - recommended default: **allowed** via the same update endpoint (`active: true`), since deactivation is the default removal path, not a terminal state. Alternative: deactivation is terminal (only hard delete afterwards). Affects: `group-members` scenario "Member is reactivated", update task.
5. **Token at rest** - recommended default: **SHA-256 hash column** (credential non-recoverable from a DB dump; lookup still one indexed comparison). Alternative: raw token column (simpler; recoverable on dump). Externally invisible; affects D2, the groups-table task.
6. **HTTP status for group creation** - recommended default: **201** with the full `GroupResponse` body. Alternative: 200. Externally visible but trivial to change before any client exists; affects the creation scenario wording ("successful creation") and the create task's assertions.
