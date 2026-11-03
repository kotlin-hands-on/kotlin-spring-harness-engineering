# Proposal

## Why

Soft delete makes accidental deletion recoverable in the ledger, but only if there is a way to undo it. Restoring is cheap on an event-sourced ledger and friendly to mistakes.

## What Changes

- Add `ExpenseRestored`: a deleted expense is restored at a new revision, subject to `expectedRevision`; restoring a live expense is rejected.
- Add the equivalent restore for settlements.
- Projections (current expenses, balances) re-include the restored item through the normal fold.

## Capabilities

### New Capabilities

### Modified Capabilities
- `expenses`: restore of a deleted expense (delta spec; capability is introduced by `expenses-equal-split`).
- `settlements`: restore of a deleted settlement (delta spec; capability is introduced by `settlements`).

## Impact

- `backend` commands and projectors, `shared` DTOs, additive ledger event types.
- Out of scope: purging, restoring group or member state, UI.
- Builds on: `expenses-equal-split` and `settlements`.
