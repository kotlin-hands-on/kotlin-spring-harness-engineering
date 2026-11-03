# Proposal

## Why

Sometimes two people pay one bill ("Anna paid 60 and Ben paid 30"). The payments list already exists in the model but accepts a single entry; this change opens it up and defines how it affects the money math.

## What Changes

- Accept several `(member, amount)` payments per expense; payments must sum exactly to the expense total and use the group currency.
- Validate payers are active group members, with no duplicate payer; sealed rejections.
- The current-expenses projection and ledger snapshot carry all payments; existing single-payer events stay valid.

## Capabilities

### New Capabilities

### Modified Capabilities
- `expenses`: payments list may contain multiple entries that must sum to the total (delta spec; capability is introduced by `expenses-equal-split`).

## Impact

- `shared` DTO validation, `backend` command validation and projection; no schema break.
- Out of scope: balances (they consume payments but are specified separately), UI.
- Builds on: `expenses-equal-split`.
