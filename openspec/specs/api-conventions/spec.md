# api-conventions Specification

## Purpose

Wire conventions shared by every endpoint of the group expense API: URL versioning with the group access token in the path, the money representation, a sealed error model with stable codes, the `seq`/`atLeastSeq` consistency naming, access-token redaction in request logs, and the contract tests that pin the JSON.

## Requirements

### Requirement: API version prefix and group token in path

All API endpoints SHALL be served under the `/api/v1` prefix. Endpoints scoped to one group SHALL address that group by its access token as a path segment: `/api/v1/groups/{token}/...`.

#### Scenario: Unmatched API route returns a JSON error

- **WHEN** a request is made to a path under `/api` for which no handler exists
- **THEN** the response has status 404 with a JSON error body of code `not_found` (no HTML fallback, no static resource content)

#### Scenario: Group-scoped routes nest under the token segment

- **WHEN** a later capability adds an endpoint scoped to one group
- **THEN** its route is nested under `/api/v1/groups/{token}/...` and the access token appears nowhere else in the request

### Requirement: Money on the wire

Monetary amounts SHALL be represented on the wire as `{"minor": <integer>, "currency": "<ISO 4217 code>"}`, where `minor` is the amount in the currency's minor units (e.g. cents). The backend SHALL map this representation to `javax.money.MonetaryAmount` and back preserving the exact minor-unit value, using the currency's default fraction digits. A currency code the backend cannot resolve SHALL be rejected as a validation failure.

#### Scenario: Exact JSON and round trip

- **WHEN** a money value of 1234 minor units in EUR is serialized
- **THEN** the JSON is exactly `{"minor":1234,"currency":"EUR"}` and decoding it yields an equal value

#### Scenario: Zero-fraction currency maps exactly

- **WHEN** 300 minor units of JPY are mapped to a `MonetaryAmount` and mapped back
- **THEN** the result is 300 JPY with no fraction digits introduced or lost

#### Scenario: Unknown currency is rejected

- **WHEN** an amount with a currency code the backend cannot resolve is mapped
- **THEN** the mapping fails as a validation failure and no `MonetaryAmount` is produced

#### Scenario: Negative amounts survive the mapping

- **WHEN** a negative minor value is mapped to `MonetaryAmount` and back
- **THEN** sign and magnitude are unchanged

### Requirement: Group payloads carry currency fraction digits

Any payload that describes a group SHALL carry the group's ISO 4217 currency code together with that currency's default fraction digits, so clients need no currency table of their own.

#### Scenario: Group payload exposes currency metadata

- **WHEN** a payload describing a group is returned by any endpoint
- **THEN** it includes the group's currency code and the fraction digits of that currency

### Requirement: Sealed error model with stable codes

Domain rejections SHALL be returned as `{"error":{"type":"<code>","message":"<text>"}}` where `<code>` is one of a closed, stable set: `validation_failed`, `not_found`, `conflict`, `currency_mismatch`. Their HTTP statuses SHALL be 400, 404, 409 and 422 respectively. New codes may be added in later changes; existing codes and their meanings SHALL NOT change.

#### Scenario: Validation failure

- **WHEN** a request is rejected as invalid input
- **THEN** the response is status 400 with error code `validation_failed`

#### Scenario: Not found

- **WHEN** a requested resource does not exist
- **THEN** the response is status 404 with error code `not_found`

#### Scenario: Conflict

- **WHEN** a write is rejected because of a conflicting state (e.g. a stale revision)
- **THEN** the response is status 409 with error code `conflict`

#### Scenario: Currency mismatch

- **WHEN** an amount's currency differs from the group's fixed currency
- **THEN** the response is status 422 with error code `currency_mismatch`

### Requirement: Consistency cursor naming

Every successful write response SHALL include a `seq` field holding the ledger sequence number the write produced. Every read endpoint SHALL accept an `atLeastSeq` query parameter and SHALL report the `seq` its response reflects. The semantics of `atLeastSeq` (waiting, staleness signalling) are owned by the ledger capability, not by this convention.

#### Scenario: Write response carries seq

- **WHEN** a successful write response is returned
- **THEN** its JSON body contains a `seq` field with an integer value

#### Scenario: Read accepts atLeastSeq

- **WHEN** a read endpoint receives a request with an `atLeastSeq` query parameter
- **THEN** the parameter is accepted as the consistency cursor and the response reports the `seq` it reflects

### Requirement: Access-token redaction in request logs

Request logs emitted by the backend SHALL NOT contain group access tokens. The token path segment (the first path segment after `/groups/`) and the value of every query parameter named `token` or `accessToken` SHALL be replaced by a fixed placeholder before the log line is emitted. Redaction SHALL be location-based, not value-based: only the locations named here are redacted. Any future token-carrying location SHALL extend the backend's single redaction function.

#### Scenario: Token path segment is redacted

- **WHEN** a request to a `/api/v1/groups/<token>/...` path is logged
- **THEN** the logged line contains the placeholder where the token segment was and does not contain the token value

#### Scenario: Token query parameter is redacted

- **WHEN** a logged request URL contains a query parameter named `token` or `accessToken`
- **THEN** the logged line contains the placeholder instead of that parameter's value

### Requirement: Wire format pinned by contract tests

Every serializable API DTO in `shared` SHALL be covered by contract tests that round-trip it and pin its exact JSON, including sealed-class discriminators, and that run on both the JVM and Wasm targets. The tests SHALL fail when the wire format changes.

#### Scenario: Wire format change breaks the contract tests

- **WHEN** the JSON shape of a covered DTO changes (renamed key, changed discriminator, added mandatory field)
- **THEN** the contract tests fail

#### Scenario: Sealed discriminator is pinned

- **WHEN** a sealed DTO subtype is serialized
- **THEN** the JSON contains the pinned discriminator key and the subtype's stable serial name

### Requirement: API DTOs are separate from ledger event payloads

API DTOs SHALL be distinct types from ledger event payload types. Neither kind SHALL be serialized in place of the other, even where their JSON shapes coincide.

#### Scenario: Ledger payload type used as a DTO is rejected

- **WHEN** a class belonging to ledger payload code depends on an API DTO type
- **THEN** the architecture rule fails

#### Scenario: Same shape, separate types

- **WHEN** a later ledger payload stores money with the same `{"minor", "currency"}` JSON shape
- **THEN** it is a separate type from the money DTO and no type alias or shared supertype connects them

### Requirement: Forward-compatible JSON on the HTTP path

The backend SHALL decode API request bodies tolerantly (unknown fields ignored) and SHALL omit response fields whose value equals their declared default, so optional fields can be added within version 1 without breaking existing clients. The HTTP layer SHALL use the same JSON configuration as the shared contract tests.

#### Scenario: Unknown request fields are ignored

- **WHEN** a request body contains a field the target DTO does not declare
- **THEN** the request decodes successfully and the unknown field is ignored

#### Scenario: Default-valued response fields are omitted

- **WHEN** a response DTO field holds its declared default value
- **THEN** the field is absent from the response JSON

#### Scenario: HTTP layer uses the contract JSON configuration

- **WHEN** the backend encodes or decodes an API DTO over HTTP
- **THEN** the observable behaviour matches the shared contract tests (same discriminator key, same omission and tolerance rules)
