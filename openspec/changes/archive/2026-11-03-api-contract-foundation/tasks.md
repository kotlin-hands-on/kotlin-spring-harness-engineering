# Tasks

Scenario references point at `specs/api-conventions/spec.md`; test boundaries are defined in `design.md` (section "Test boundaries"). Every task leaves `./gradlew check` green (JVM and Wasm contract tests, backend integration tests, ArchUnit, Detekt).

## 1. Shared contract JSON foundation

- [x] 1.1 Create `com.example.harnessengineeringspring.shared.api.ApiJson` exposing the contract configuration as a `JsonBuilder` extension plus a ready `Json` (`ignoreUnknownKeys=true`, `encodeDefaults=false`, `explicitNulls=false`, default `"type"` discriminator, non-lenient), and a `commonTest` that pins each setting's observable behaviour (unknown key ignored on decode, defaulted field omitted on encode, discriminator emitted). Verify `:shared:jvmTest` and `:shared:wasmJsNodeTest` both pass (Scenarios: "Unknown request fields are ignored", "Default-valued response fields are omitted", contract side of "HTTP layer uses the contract JSON configuration").
- [x] 1.2 Add `MoneyDto(minor: Long, currency: String)` to `shared.api` with a contract test pinning the exact JSON `{"minor":1234,"currency":"EUR"}` and the round trip, on both targets (Scenarios: "Exact JSON and round trip", "Sealed discriminator is pinned" applies to the shared suite as a whole). Verify both `:shared` test tasks pass.
- [x] 1.3 Add the sealed `ApiError` (`validation_failed`, `not_found`, `conflict`, `currency_mismatch` serial names, common `message`) and the `ErrorResponse` envelope to `shared.api`, with contract tests pinning each subtype's exact discriminator JSON and the envelope shape `{"error":{"type":...,"message":...}}` (Scenario: "Sealed discriminator is pinned"). Verify both `:shared` test tasks pass.

## 2. Backend JSON parity on HTTP

- [x] 2.1 Apply `ApiJson`'s builder configuration to the backend's HTTP `Json` (whichever hook Boot 4.1 supports - customizer bean or user `Json` bean; record the chosen hook in the task summary), then extend the probe tests (MockMvc, shared `@IntegrationTest` boundary) to assert over HTTP that a request body with an unknown field is accepted and a defaulted DTO field is omitted from the response (Scenarios: "Unknown request fields are ignored", "Default-valued response fields are omitted", "HTTP layer uses the contract JSON configuration"). Verify `:backend:test` green. Chosen hook, recorded per this task's instruction: the `KotlinxSerializationJsonBuilderCustomizer` bean `apiContractJsonCustomizer` in `ApiJsonConfiguration`, which applies `ApiJson.contract` to Boot's auto-configured `Json`; it is deliberately not `Ordered`, so it runs after Boot's order-0 standard customizer and the contract wins over `spring.kotlinx.serialization.json.*` properties.

## 3. Money mapping in the backend

- [x] 3.1 Implement the `MoneyDto -> MonetaryAmount` mapper and its reverse as pure functions in the backend `money` package, returning a sealed result for the unknown-currency case (never throwing for a domain condition). Plain JUnit tests, no Spring context (boundary: mapper unit): 1234 EUR minors -> 12.34, 300 JPY -> 300 JPY with 0 fraction digits, negative amounts survive both directions, unknown code yields the `UnknownCurrency` result (Scenarios: "Zero-fraction currency maps exactly", "Negative amounts survive the mapping", "Unknown currency is rejected", "Exact JSON and round trip" wire side is covered by 1.2). Verify `:backend:test` green.

## 4. Error model on HTTP

- [x] 4.1 Add backend `ApiException(val error: ApiError)` and `ApiErrorAdvice` - the single status map (400/404/409/422 per the D4 table) and envelope renderer, plus rendering of the container's no-handler exception for `/api/**` paths as `not_found`. Add a test-only probe endpoint per error code and MockMvc tests asserting status, `type` code and envelope shape for all four codes, and that an unknown `/api/v1/...` path returns the 404 `not_found` envelope (Scenarios: "Validation failure", "Not found", "Conflict", "Currency mismatch", "Unmatched API route returns a JSON error"). Verify `:backend:test` green.

## 5. Consistency naming probes

- [x] 5.1 Extend the test-only probe surface with a write-shaped endpoint whose response carries `seq` and a read-shaped endpoint accepting `atLeastSeq` and echoing the `seq` it reflects; MockMvc tests pin both names so later changes cannot rename them (Scenarios: "Write response carries seq", "Read accepts atLeastSeq"). No production code implements semantics - that stays with `ledger-core`. Verify `:backend:test` green.

## 6. Token redaction in request logs

- [x] 6.1 Implement the pure `redact(target)` function (first path segment after `/groups/`, query parameters `token`/`accessToken`, fixed placeholder `[REDACTED]`) with unit tests over path and query shapes, including invalid/malformed tokens (Scenarios: "Token path segment is redacted", "Token query parameter is redacted").
- [x] 6.2 Register the request-log filter (INFO, one line on every path: method, redacted target, status — or, when the chain throws, the most specific cause of the exception in the status's place, rethrown untouched) for `/api/*` and add an integration test that captures the emitted line with a Logback list appender, asserting the token value is absent, the placeholder is present, and the request is still served normally (same scenarios, end to end). Verify `:backend:test` green.

## 7. Architecture rule for DTO/payload separation

- [x] 7.1 Add the ArchUnit rule to `ArchitectureTest`: no class in `..ledger..` or `..persistence..` may depend on `..shared.api..`, with `allowEmptyShould(true)` (design D7). Prove it bites once by temporarily adding a production class in a `ledger` package that references a shared API DTO, running `:backend:test` to see the rule fail, then reverting the violation (Scenarios: "Ledger payload type used as a DTO is rejected"; "Same shape, separate types" is realized by `ledger-core`). Verify `:backend:test` green after the revert.

## 8. Integration check

- [x] 8.1 Run `./gradlew check` across all modules and confirm it is green end to end: shared contract tests on JVM and Wasm, backend integration tests (JSON parity, error model, seq naming, redaction), ArchUnit rules, Detekt clean on the new production code.

## Workflow follow-up

- Archive the change after the project's review requirements are satisfied; archiving creates the first main spec at `openspec/specs/api-conventions/spec.md`.
- Verify the archived result.
