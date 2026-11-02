---
name: tdd
description: Test-driven development, language- and framework-agnostic. Use when the user wants to build new features or fix bugs test-first, writes, reviews or fixes tests (unit or integration), refactors, or mentions "red-green-refactor" - black-box behaviour tests through public boundaries, zero-mocking-first with fakes, good vs bad tests and mocks. Pair with a stack skill (`kotlin-testing`, `spring-testing`) for tooling and idioms.
---

# Test-Driven Development

TDD is the red -> green loop. This skill is the reference that enables production of tests worth keeping. It determines what a good test is, where tests go, antipatterns to avoid. Consult before and during development, not after.

Stack-specific skills build on this one - load the one matching the code under test:

| Stack | Skill |
|---|---|
| Kotlin / JVM - JUnit 6, kotlin.test, power-assert, coroutines, Kotlin test idioms | `kotlin-testing` (`../kotlin-testing/SKILL.md`) |
| Spring Boot - context caching / minimal contexts, public-API entry points (service, HTTP, Kafka, repository), Testcontainers, `@TestConfiguration` fakes | `spring-testing` (`../spring-testing/SKILL.md`) |
| A mock is truly unavoidable (Kotlin) | `mockk` (`../mockk/SKILL.md`) |
| Architecture rules (layers, dependency direction, cycles, conventions) - the one deliberate white-box test kind | `archunit` (`../archunit/SKILL.md`) |

## What a good test is

Tests verify behavior through public interfaces, not implementation details. Code can change, tests shouldn't. 
Good tests read like specifications. They should tell you what capabilities exist and survive refactor, because they don't care about internal structure and implementation details.

Every test - including unit tests - treats the subject as a box: put inputs in, assert on what comes out (return value, raised error, state visible through the public API, calls that reached a *fake* collaborator). Never assert *how* the box did it.

## Where tests go

Tests should live at the public boundary where behavior can be observed; never test against internals.

**Test only at pre-agreed boundaries.** Before writing tests, write down the boundaries under test, each with a one-line note on what it catches and what it misses. Tests at the wrong boundary are the most expensive mistake to undo, so agree on them first:

- A spec, design or task already names the boundaries (OpenSpec `design.md`, issue) → use those and state them; no need to ask.
- Otherwise ask the user: "What's the public boundary that we should test?"
- Running without a user to ask (subagent, batch apply) → pick the outermost public boundary that observes the behaviour, state the assumption in your output and continue.

## Rules

- **Red before green.** Write the failing tests first, only add stubs to make them compile. Only write passing code after.
- **Refactor is not part of the loop.** It belongs to the review stage (`change-review` skill): keeping red -> green minimal makes each step small and verifiable, and the review sees the green code with fresh, independent eyes before it is reshaped.
- **Every bug fix ships with a red-then-green test** that fails on the old code with the *user-visible* symptom.

## MUST DO

- **Test through the public API only.** Construct the real subject, call public functions, assert on results. Never widen visibility to test something - the test is at the wrong level.
- **Assert observable outcomes, in this order of preference:** return value (incl. result/error objects) → raised error (non-domain failures) → state visible afterwards through the API → side effects that crossed a boundary, observed on a fake.
- **Zero mocking by default.** Real thing → hand-rolled fake → stub value → only then a mocking library (with a one-line justification). Mocks encode *how* the subject talks to collaborators, so they break on refactors that keep behaviour intact. Details → `references/mocking.md`.
- **One behaviour per test**, named after the behaviour, not the method: `rejects registration when email is already taken`.
- **Arrange / Act / Assert** visually separated; the *act* is a single call. Fixture builders with defaults instead of shared mutable setup.
- **Deterministic inputs.** Inject clocks, seeded randomness, id generators; never sleep - a test that can fail without a code change trains everyone to ignore red.
- **Cover the contract, not the lines:** happy path, each domain failure, boundaries (empty, one, many, max), invalid input, idempotency where relevant. Table-driven / parameterized tests for value tables.
- **Integration tests are also black-box:** real wiring, real infrastructure where feasible (containers), assert on the boundary's responses and read back through the same boundary.

## MUST NOT DO / Anti-patterns

- **Coupling to implementation**: mocks collaborators that are internal to the boundary, test private methods, verifies through a side-channel (like db queries) instead of using the boundary.
- **Tautologies**: assertions that recompute expected values the way the code does.
- **White-box assertions:** "was called", call counts, call order, "helper was invoked".
- **Mocking what you own** (own classes, value types, repositories behind an interface, clocks) or easy-to-build types (collections, dates); **no partial mocks/spies of the subject**.
- **Test-only production code** (`forTests()`, "visible for testing", skip-validation flags).
- **Shared mutable state between tests**, ordering dependencies, logic (`if`/loops) in tests, disabled tests without an issue link, always-true assertions, swallowed exceptions.
- **Snapshot-everything assertions** - assert the fields the behaviour is about.
- **Asserting log output / string representations** unless that string *is* the product.
- **Testing value types, getters, mapping code or the standard library** in isolation.

## References

| Load when | File |
|---|---|
| Writing or reviewing tests - good vs bad tests, what to assert, test smells, contract coverage | `references/tests.md` |
| A collaborator is inconvenient / considering a mock - when to mock, substitution order, fakes, good vs bad mocking | `references/mocking.md` |
