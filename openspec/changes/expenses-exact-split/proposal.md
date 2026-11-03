# Proposal

## Why

Equal splits do not cover "I had the steak". Exact amounts are the simplest second split type and verify that the `SplitIntent` extension point established earlier really is extensible.

## What Changes

- Add an `Exact` `SplitIntent`: an explicit amount per participating member.
- Validate on the server that the amounts sum exactly to the expense total and are in the group currency; reject otherwise with a sealed result.
- Extend the contract, ledger snapshot payload and allocation function accordingly; existing events remain readable.

## Capabilities

### New Capabilities

### Modified Capabilities
- `split-allocation`: adds the exact split intent and its validation rules (delta spec; capability is introduced by `expenses-equal-split`).

## Impact

- `shared` (new `SplitIntent` subtype), `backend` allocation and validation, ledger payload (additive).
- Out of scope: shares/percent, multiple payers, preview endpoint, UI.
- Builds on: `expenses-equal-split`.
