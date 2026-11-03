# Proposal

## Why

Because anyone with the link can edit anything, trust rests on transparency: users must be able to see what happened. Every money mutation is already recorded in the ledger; this change shows it to them.

## What Changes

- A feed endpoint listing a group's money events (expenses and settlements, including revisions, deletes and restores) newest first, paginated with the ledger `seq` as cursor.
- Entries state the actor (self-declared, honor system), the time, and for revisions a read-time diff between two adjacent snapshots (e.g. amount 30.00 to 36.00).
- Member names are resolved at read time, so a rename shows everywhere. Group and member changes do not appear (they are not recorded).
- The feed supports `atLeastSeq`.

## Capabilities

### New Capabilities
- `activity-feed`: read model and endpoint presenting the ledger's money history with diffs.

### Modified Capabilities

## Impact

- `backend` read side over `ledger_event`, `shared` feed DTOs.
- Out of scope: group/member change history, notifications, UI, filtering and search.
- Builds on: `expenses-equal-split` (further event types appear as later changes land).
