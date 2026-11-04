# Tasks

Vertical slices: every numbered section below (except the single foundation task 1 and the final checks) lands a working, tested slice through the public HTTP API - shared DTO + backend endpoint + persistence + tests through the public boundary in the same task group. Scenario references point at this change's `specs/group-access/spec.md` / `specs/group-members/spec.md`, and at `openspec/specs/api-conventions/spec.md` for the shared conventions; test boundaries are defined in `design.md` (section "Test boundaries"). Every task leaves `./gradlew check` green (JVM and Wasm contract tests, backend integration tests, ArchUnit, Detekt).

## 1. Foundation: groups table and first migration

- [x] 1.1 Define the Exposed `Groups` table in `...harnessengineeringspring.persistence` (id uuid pk, name, currency code, unique token hash - see design D2/D6), generate the first Flyway migration V1 with the configured Exposed Gradle plugin, and verify the context still starts with all migrations applied: `PersistenceSmokeTest` green, `flyway.info().pending()` empty (`:backend:test`).

## 2. Slice: create a group (first working endpoint)

- [x] 2.1 Add `CreateGroupRequest(name, currency)` and `GroupResponse(id, name, currency, fractionDigits, token = null, seq)` to `shared.api` (design D3: `seq` must NOT be defaulted) with contract tests pinning the exact creation JSON (token present, `seq` present even at 0) and the read JSON (token absent, defaulted field omitted), on both JVM and Wasm (`:shared:jvmTest`, `:shared:wasmJsNodeTest`).
- [x] 2.2 Implement the token generator (32 bytes `SecureRandom`, URL-safe Base64-no-padding) and its SHA-256 at-rest hashing plus the `GroupToken`/`GroupId` value classes (design D2/D3), with plain JUnit tests for length, URL-safe charset, sample uniqueness, and hash determinism (boundary: backend unit).
- [x] 2.3 Add the currency-code resolver to the backend `money` package (sealed result: resolved code + default fraction digits / `UnknownCurrency`), reusing the `MoneyMapping` convention, with plain JUnit tests for EUR (2 digits), JPY (0 digits) and an unknown code (boundary: backend unit).
- [x] 2.4 Implement `GroupService.create` returning a sealed result (`InvalidName`, `UnknownCurrency`, `Created`) and the `POST /api/v1/groups` controller translating to `ApiException` (design D4), and verify through MockMvc (shared `@IntegrationTest` boundary): 201 response with id, name, currency, fractionDigits, token and `seq`; blank name -> 400 `validation_failed` envelope; unknown currency -> 400 `validation_failed` envelope; two creations return distinct tokens and both rows persist with hashed tokens (group-access scenarios "Group is created with name, currency and token", "Blank group name is rejected", "Unresolvable currency code is rejected", "Two creations issue distinct tokens").

## 3. Slice: read a group by token

- [x] 3.1 Implement the token-hash lookup in `GroupService` (sealed `GroupNotFound`/`GroupFound`) and `GET /api/v1/groups/{token}`, and verify through MockMvc: 200 response with id, name, currency and fractionDigits and NO token field; `atLeastSeq` query parameter accepted and `seq` reported; unknown token -> 404 `not_found` envelope; malformed token -> 404 `not_found`, indistinguishable; the request-log line for this real route shows the `[REDACTED]` placeholder and never the token value (group-access scenarios "Group is read by its token", "Unknown token is not found", "Malformed token is not found"; api-conventions scenarios "Group payload exposes currency metadata", "Read accepts atLeastSeq", "Token path segment is redacted").

## 4. Slice: rename a group

- [x] 4.1 Add `UpdateGroupRequest(name)` to `shared.api` (contract test pinning its JSON on both targets) and implement the rename branch of the group endpoint (`PATCH /api/v1/groups/{token}`, only `name` accepted; a `currency` field is an unknown field, ignored - design D5), and verify through MockMvc: rename reflected in a subsequent read with the same id and unchanged currency/fraction digits; a body also carrying a `currency` field leaves the currency unchanged; blank name -> 400 `validation_failed` with the name unchanged; unknown token -> 404 `not_found` (group-access scenarios "Group is renamed", "Blank name in rename is rejected", "Rename of an unknown token", "Rename leaves the currency unchanged", "A rename request carrying a currency field does not change it").

## 5. Slice: add and list members

- [x] 5.1 Define the Exposed `Members` table (id uuid pk, group_id -> `groups.id`, name, active; no unique name constraint per design D6/open question 3), generate Flyway migration V2 with the Exposed plugin, and verify all migrations apply: `PersistenceSmokeTest` green (`:backend:test`).
- [x] 5.2 Add `AddMemberRequest(name)`, `MemberResponse(id, name, active, seq)` and `MemberListResponse(members, seq)` to `shared.api` (design D3: `active`/`seq` not defaulted, always emitted) with contract tests pinning the exact JSON for a member, a list, and the empty list on both targets.
- [x] 5.3 Implement the group-scoped `MemberService` (sealed results; members looked up only within the addressed group) with `POST /api/v1/groups/{token}/members` and `GET /api/v1/groups/{token}/members` (list ordered by name, then id), and verify through MockMvc: add returns id, name, active true and `seq`, and the member appears in the list; empty group returns an empty list; list ordered by name with mixed active states; blank name -> 400 `validation_failed` with no member created; unknown token -> 404 `not_found` (group-members scenarios "Member is added", "Blank member name is rejected", "Add with an unknown group token", "List shows all members with their state", "Empty group yields an empty list", "List with an unknown group token").

## 6. Slice: update a member (rename, deactivate, reactivate)

- [x] 6.1 Add `UpdateMemberRequest(name = null, active = null)` to `shared.api` (contract test pinning that absent fields are omitted from the JSON on both targets) and implement `PATCH /api/v1/groups/{token}/members/{memberId}` (absent field = unchanged; blank name when present -> rejection), and verify through MockMvc: rename reflected in the list with id and active unchanged; deactivate sets inactive while the member stays listed; reactivating an inactive member succeeds; an empty update returns the member unchanged; blank name -> 400 `validation_failed` with the member unchanged; unknown member id -> 404 `not_found`; a member id belonging to a different group -> 404 `not_found` under this group's token (group-members scenarios "Member is renamed", "Member is deactivated", "Member is reactivated", "Absent fields leave the member unchanged", "Blank name in update is rejected", "Another group's member id is not found").

## 7. Slice: delete a member

- [x] 7.1 Implement `DELETE /api/v1/groups/{token}/members/{memberId}` (hard delete via the Exposed DSL) and verify through MockMvc: an unreferenced member - active as well as deactivated - is removed from the list and afterwards addressable only as 404 `not_found`; unknown member id -> 404 `not_found`; another group's member id -> 404 `not_found` (group-members scenarios "Unreferenced member is deleted", "Delete with an unknown member id", "Another group's member id is not found").
- [x] 7.2 Translate a database referential-integrity violation on member delete into `ApiError.Conflict` (design D6), and verify through MockMvc with a test-scoped stand-in table carrying an `ON DELETE RESTRICT` foreign key to `members(id)` (created, referenced and dropped by the test itself): deleting the referenced member returns 409 `conflict` and the member remains; without the reference the same deletion succeeds (group-members scenario "Referenced member deletion is rejected"; the real `ledger_event` FK arrives with `ledger-core`).

## 8. Slice: the group-currency boundary gate

- [x] 8.1 Implement the `GroupAccess` gate service (resolve group by token; validate an incoming `MoneyDto` against the group's fixed currency via `MoneyMapping` - sealed results `GroupNotFound`, `UnknownCurrency`, `CurrencyMismatch`, `Accepted`) and exercise it through a test-only probe endpoint under `/api/__probe/*` (design D5; the probe convention), verifying through MockMvc: an amount in the group's currency is accepted; an amount in a different currency -> 422 `currency_mismatch` envelope; an unresolvable currency code -> 400 `validation_failed`; unknown token -> 404 `not_found` (group-access scenarios "Amount in the group's currency is accepted", "Amount in a different currency is rejected", "Amount with an unresolvable currency is rejected"). Later amount-carrying endpoints call this same gate; the probe is retired with them.

## 9. Architecture rule and integration check

- [x] 9.1 Add the ArchUnit rule to `ArchitectureTest`: no class annotated `@RestController` depends on `..persistence..` or any `org.jetbrains.exposed` package (design D8). Prove it bites once by temporarily adding a controller that references an Exposed table, running `:backend:test` to see the rule fail, then reverting the violation; verify `:backend:test` green after the revert.
- [x] 9.2 Run `./gradlew check` across all modules and confirm it is green end to end: shared contract tests on JVM and Wasm, backend integration tests (group lifecycle, member lifecycle, currency gate, delete guard, redaction), ArchUnit rules, Detekt clean on the new production code.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied; archiving creates the main specs at `openspec/specs/group-access/spec.md` and `openspec/specs/group-members/spec.md`.
- Verify the archived result.
