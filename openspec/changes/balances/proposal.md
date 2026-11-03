# Proposal

## Why

Users need to know where they stand: who is owed money and who owes. Balances are the most important derived view of the ledger and the first projection computed purely from money events.

## What Changes

- A net-balance projection: one `MonetaryAmount` per member in the group currency, derived from expense events (paid minus owed shares). Balances always sum to exactly zero.
- Deleted expenses stop contributing; a revision replaces the effect of the previous snapshot, handled idempotently with the projector checkpoint.
- Deactivated members stay in balances until settled.
- A balances read endpoint supporting `atLeastSeq` and reporting the `seq` it reflects.
- The projection can be rebuilt from scratch by replaying the ledger.

## Capabilities

### New Capabilities
- `balances`: per-member net balance projection and its read endpoint.

### Modified Capabilities

## Impact

- `backend` projector, projection table (Exposed DSL), endpoint; `shared` balance DTOs.
- Property tests: sum of balances is zero after any sequence of create, revise and delete.
- Out of scope: suggested transfers, settlements (which later feed into balances), UI.
- Builds on: `expenses-equal-split`.
