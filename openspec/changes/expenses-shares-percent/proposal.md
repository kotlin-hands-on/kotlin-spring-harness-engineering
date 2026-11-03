# Proposal

## Why

Rooms of different sizes or a couple counted as two people need weighted splits. Percentages are a special case of weights, so both can share one mechanism.

## What Changes

- Add `Shares` (integer weights per member) and `Percent` (percentages summing to 100) as `SplitIntent` subtypes.
- Allocation distributes the total proportionally with the same deterministic remainder-cent rule, so shares always sum exactly to the total.
- Validation: positive weights, percentages sum to 100, participants are active group members; sealed rejections.
- Contract, ledger snapshot payload and allocation are extended additively.

## Capabilities

### New Capabilities

### Modified Capabilities
- `split-allocation`: adds weighted (shares) and percentage split intents (delta spec; capability is introduced by `expenses-equal-split`).

## Impact

- `shared`, `backend` allocation and validation, ledger payload (additive).
- Property tests for proportional allocation including awkward ratios and zero-fraction currencies.
- Out of scope: multiple payers, preview endpoint, UI.
- Builds on: `expenses-equal-split` (independent of `expenses-exact-split`).
