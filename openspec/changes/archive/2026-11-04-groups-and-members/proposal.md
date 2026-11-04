# Proposal

## Why

Everything in the app lives inside a group. With no accounts (anonymous, link-based), the group and its secret token are the whole identity and access model, and the group's currency constrains all money handling.

## What Changes

- Create a group with a name and a fixed ISO 4217 currency; the server issues a high-entropy secret token. The link is the only credential and a lost link means a lost group.
- Read and rename a group by token; the currency cannot change after creation.
- Add, rename, deactivate and list members. Members are group-scoped (no user accounts).
- Member removal rule: deactivate by default; hard delete only while the member is referenced by no ledger event (enforced by an `ON DELETE RESTRICT` foreign key).
- Group and member state are plain CRUD tables via Exposed (not event-sourced). Their changes are not recorded in the ledger or activity feed; names are resolved at read time.
- Boundary validation: any incoming amount whose currency differs from the group's is rejected with a `CurrencyMismatch` result. Monetary columns use `exposed-money` so persistence enforces currency too.
- First Flyway migration generated with the Exposed plugin.

## Capabilities

### New Capabilities
- `group-access`: group creation, secret-token access, fixed currency, currency validation of incoming amounts.
- `group-members`: member lifecycle (add, rename, deactivate, conditional delete) within a group.

### Modified Capabilities

## Impact

- `backend` (Exposed tables, services, controllers), `shared` (group/member DTOs), first migration.
- Out of scope: ledger, expenses, any UI, member claim/accounts, invites beyond sharing the link.
- Builds on: `api-contract-foundation`.
