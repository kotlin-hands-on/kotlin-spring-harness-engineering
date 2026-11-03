# Proposal

## Why

All money is event-sourced per group: the ledger records every money mutation, and balances, current expenses and the feed are derived from it. The event store and the asynchronous projection mechanism are shared infrastructure that every money capability needs, so they come before any expense.

## What Changes

- Add an append-only `ledger_event` table in Postgres via Exposed: `(id, group_id, seq, type, payload jsonb, actor_member_id, occurred_at)` with `UNIQUE(group_id, seq)` for per-group ordering and optimistic concurrency.
- Payloads are kotlinx.serialization types owned by the ledger, separate from API DTOs, explicitly versioned, money stored as `{minor, currency}`.
- Hand-written Flyway migration adding a `pg_notify` trigger on the events table (the Exposed plugin cannot generate triggers).
- A dedicated raw pgjdbc connection (outside the Exposed pool) runs `LISTEN`; notifications are only a wake-up hint.
- Projector framework: per-projector, per-group checkpoint (`last_seq`), idempotent handlers, catch-up from the table at startup and on a periodic fallback poll. Projection tables are written and read with the Exposed DSL; computations run in the backend on `MonetaryAmount`.
- Read-your-writes: write responses return `seq`; read endpoints accept `atLeastSeq` and long-poll until the projector reaches it, with a timeout that returns current state flagged as behind.
- Assumption: single backend instance, no advisory locking. Actor identity is self-declared (honor system).

## Capabilities

### New Capabilities
- `ledger`: append-only per-group event store, ordering, versioned payloads.
- `projections`: asynchronous projection runtime, checkpoints, catch-up, and the `atLeastSeq` read guarantee.

### Modified Capabilities

## Impact

- `backend` persistence and runtime wiring; two migrations (generated table, hand-written trigger); ArchUnit rule that group/member state is never read back from events.
- Out of scope: concrete money event types and projections (expenses, balances, settlements), the feed.
- Builds on: `groups-and-members`.
