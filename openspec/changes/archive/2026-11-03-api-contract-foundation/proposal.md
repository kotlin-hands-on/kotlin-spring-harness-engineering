# Proposal

## Why

Backend and web client are separate programs that agree only through the `shared` module. Conventions that every endpoint will repeat (versioning, money on the wire, errors, access token handling, consistency cursors) must be fixed once, before any feature endpoint exists, or each feature will invent its own.

## What Changes

- Establish the API under `/api/v1/...`; the group access token is a path segment (`/api/v1/groups/{token}/...`).
- Define `MoneyDto { minor: Long, currency: String }` (ISO 4217 code) in `shared`; the backend maps it to `javax.money.MonetaryAmount`. The group payload carries the currency's fraction digits so the client needs no currency table.
- Define the sealed error/result model returned for domain rejections (validation failure, not found, conflict, currency mismatch) with stable error codes.
- Define the consistency convention: write responses return the ledger `seq`; reads accept `atLeastSeq` (semantics implemented in `ledger-core`).
- Require access-token redaction in request logs.
- Contract tests in `shared` round-trip every DTO and pin the JSON, including sealed-class discriminators.
- API DTOs are separate types from ledger event payloads.

## Capabilities

### New Capabilities
- `api-conventions`: versioning, money representation, error model, token-in-path, consistency cursor, log redaction.

### Modified Capabilities

## Impact

- `shared` module (DTOs, serializers, contract tests); backend HTTP configuration for kotlinx.serialization and token redaction.
- Out of scope: concrete resource endpoints (groups, expenses, ...), the ledger itself, any client code.
- Builds on: `build-foundation`.
