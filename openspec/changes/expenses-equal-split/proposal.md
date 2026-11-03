# Proposal

## Why

Expenses are the core of the product. This is the first full vertical slice through the ledger: it fixes the expense event model, the split-intent extension point, and the deterministic money allocation that every later split type and the balance math rely on.

## What Changes

- Create, revise and delete an expense in a group. Revisions are immutable full snapshots (`ExpenseCreated`, `ExpenseRevised`, `ExpenseDeleted` tombstone) with a stable expense id and a revision number.
- Edits and deletes send `expectedRevision`; a mismatch returns a conflict result.
- Payments are modeled as a list of `(member, amount)` from the start; this change accepts exactly one entry.
- A sealed `SplitIntent` in the contract; this change implements only `Equal` over a set of participating members. The client sends intent, never computed amounts.
- Server-side allocation using `MonetaryAmount`: deterministic remainder-cent distribution (by member order) so shares sum exactly to the total; sealed rejections (not exceptions) for invalid input, inactive members, currency mismatch.
- A "current expenses" projection (latest non-deleted snapshot per expense) built through `ledger-core`.
- Write responses return the ledger `seq`.

## Capabilities

### New Capabilities
- `expenses`: expense lifecycle (create, revise, delete), snapshot revisions, optimistic concurrency, current-expense read model.
- `split-allocation`: split intents and deterministic allocation of a monetary amount across members, equal split only.

### Modified Capabilities

## Impact

- `backend` (commands, allocation, projector, endpoints), `shared` (expense DTOs, `SplitIntent`), ledger payload types.
- Property-style tests: allocated shares always sum exactly to the total, including zero-fraction currencies such as JPY.
- Out of scope: other split types, multiple payers, balances, settlements, restore, feed, UI, split preview endpoint.
- Builds on: `ledger-core`.
