# Proposal

## Why

Balances never shrink unless people can record that money changed hands. Settlements are money events like expenses, so they follow the same ledger rules.

## What Changes

- Record a settlement `(from member, to member, amount)` as a ledger event, with the same defaults as expenses: immutable full-snapshot revisions, `expectedRevision` on edits, tombstone delete, group-currency validation, sealed rejections.
- The balances projector folds settlements in: the payer's balance rises and the receiver's falls by the amount; the sum stays exactly zero.
- Write responses return the ledger `seq`; a current-settlements projection backs listing.
- Honor system: settlements are self-declared and not confirmed by the receiver.

## Capabilities

### New Capabilities
- `settlements`: settlement lifecycle (record, revise, delete) as ledger events and the current-settlements read model.

### Modified Capabilities
- `balances`: balances incorporate settlement events (delta spec; capability is introduced by `balances`).

## Impact

- `backend` commands, projector changes, endpoints; `shared` settlement DTOs; additive ledger event types.
- Out of scope: restore of deleted settlements (see `expense-restore`), receiver confirmation, payment integrations, UI.
- Builds on: `balances`.
