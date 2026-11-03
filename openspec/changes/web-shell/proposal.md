# Proposal

## Why

The product is a web app, and the group link is its entry point. Before any feature screen exists, the frontend needs its runtime: Wasm app, routing, API client and the anonymous "who am I" convention.

## What Changes

- Compose for Web (Wasm) app skeleton in `web` with routing; `/g/<token>` is a real deep link and unknown routes fall back to the app.
- Ktor client configured with kotlinx.serialization, consuming the `shared` contract. The base URL is configurable and defaults to relative `/api`.
- Create-group and open-group-by-link flows at shell level; the group token lives in the URL only.
- "I am X": the chosen member is remembered per group in `localStorage` and sent as the self-declared actor. It is a convenience, not enforced.
- Handling of the `seq`/`atLeastSeq` convention and sealed error results in the client layer.
- Spring serves the built bundle (same origin); a dev-server proxy to the backend for development.
- Requirement: Wasm GC-capable browser.

## Capabilities

### New Capabilities
- `web-shell`: app runtime, routing and deep links, API client, group entry and member identity selection.

### Modified Capabilities

## Impact

- `web` module, backend static-resource and SPA-fallback configuration, Gradle wiring between `web` and `backend`.
- Out of scope: feature screens (expenses, balances, settlements, feed), which follow one proposal per backend capability once the backend changes settle.
- Builds on: `api-contract-foundation` (needs `groups-and-members` endpoints to be useful end to end).
