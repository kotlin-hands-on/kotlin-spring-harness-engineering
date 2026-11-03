# Proposal

## Why

Net balances tell users who is owed and who owes, but not who should pay whom. A small suggested-transfer list turns balances into a concrete settle-up plan.

## What Changes

- A pure function from net balances to a list of transfers `(from, to, amount)`: greedy pairing of the largest debtor with the largest creditor, at most n-1 transfers, deterministic tie-breaking by member order.
- Computed at read time from the balances projection and never stored; exposed as an endpoint supporting `atLeastSeq`.
- Applying all suggested transfers brings every balance to exactly zero.

## Capabilities

### New Capabilities
- `suggested-transfers`: deterministic settle-up suggestions derived from net balances.

### Modified Capabilities

## Impact

- `backend` pure domain function plus endpoint; `shared` transfer DTOs.
- Unit and property tests with no database: balances settle to zero, transfer count never exceeds n-1, result is stable across calls.
- Out of scope: recording payments (see `settlements`), optimal minimum-transfer search, UI.
- Builds on: `balances` (the function itself only needs the balance shape and can be developed in parallel).
