# Tests - good vs bad

A good test treats the subject as a box: put inputs in through the public boundary, assert on what comes out. It reads like a specification and survives refactoring. A bad test knows *how* the box works and breaks whenever the internals change.

Examples below are language-neutral pseudocode. Stack-specific renderings live in the stack skills (e.g. `kotlin-testing` → `references/examples.md`).

## What to assert (order of preference)

1. **Return value** - including result / error objects (`result is EmailTaken`).
2. **Raised error** for non-domain failures; assert the message only when it is part of the contract.
3. **State visible afterwards through the public API** (`orders.find(id).status == Paid`).
4. **Side effects that crossed a boundary**, observed on a *fake* (`mailer.sent == [WelcomeMail(to = email)]`).

If none of these is observable, the behaviour is not worth a test - or the design lacks an observable outcome.

## Good vs bad

### Name after behaviour, not method

Bad: `testRegister`, `register2`.

Good - one behaviour per test, the name is the specification: `registers a new user and sends a welcome mail`, `rejects a duplicate email without sending mail`.

### Implementation coupling vs public boundary

Bad - widens visibility to test an internal helper:

```
// production: normalize(email) was private, made public for the test
test "normalize lowercases":
    assert service.normalize("A@B.C") == "a@b.c"
```

Good - the same behaviour through the boundary:

```
test "treats emails case-insensitively when checking for duplicates":
    users.save(aUser(email = "a@b.c"))

    result = service.register(email = "A@B.C", name = "Bob")

    assert result is EmailTaken
```

### Tautology vs literal expectation

Bad - recomputes the expected value the way the code does (passes even when the formula is wrong):

```
assert calculator.gross(price) == price * VAT_RATE
```

Good - an independently known literal:

```
assert calculator.gross(100) == 119
```

### Side-channel vs boundary

Bad - verifies through a raw DB query instead of the boundary under test:

```
http.post("/orders", orderJson)
assert db.query("select count(*) from orders") == 1
```

Good - read back through the same public boundary:

```
location = http.post("/orders", orderJson).expectStatus(201).header("Location")
assert http.get(location).json("status") == "PENDING"
```

### Snapshot-everything vs focused assertion

Bad - fails for any unrelated field change:

```
assert result.order == Order(id = 7, status = Paid, createdAt = T0, items = [...], customer = ..., notes = "")
```

Good - assert the fields the behaviour is about:

```
assert result is Paid
assert result.order.status == Paid
```

### Shared mutable fixture vs per-test fixture

Bad - global/static state, hidden setup that runs for every test, test order dependencies:

```
static users = InMemoryUserRepository()     // leaks between tests
beforeEach: users.save(aUser())             // hidden precondition for every test
```

Good - fixture built per test, preconditions visible in *Arrange*, builders with defaults:

```
test "rejects a duplicate email":
    users = InMemoryUserRepository()
    service = RegistrationService(users, RecordingMailer(), FixedClock(T0))
    users.save(aUser(email = "a@b.c"))

    result = service.register(email = "a@b.c", name = "Bob")

    assert result is EmailTaken
```

### Logic in tests vs parameterized table

Bad:

```
test "chooses tier":
    for q in [0, 1, 999, 1000]:
        if q == 0: assert tierFor(q) == FREE
        else if q < 1000: assert tierFor(q) == STANDARD
        else: ...
```

Good - one row per case, each reported separately:

```
parameterized test "chooses shipping tier by quantity" with rows:
    (0, FREE), (1, STANDARD), (999, STANDARD), (1000, BULK)
  (quantity, expected) -> assert tierFor(quantity) == expected
```

Don't over-parameterize: a 30-row table where each row hits a different code path is 30 unreadable tests - split by behaviour.

### Non-deterministic vs deterministic

Bad - sleeping, wall-clock "now" in expectations, random ids generated inside the subject with no injection point.

Good - inject a clock, a seeded random source, an id-generator port; use virtual time for async code.

### Bug fix without vs with a regression test

Bad - fix the code, "verified manually".

Good - first a test that fails on the old code with the *user-visible* symptom (red), then the fix (green).

## Bad-test smells (symptom → fix)

| Smell | Fix |
|---|---|
| Breaks on every refactor | White-box call verification / private calls asserted - assert result, state or fake |
| Passes alone, fails in suite | Shared mutable state / static singleton / leaked global mock - build fixtures per test |
| Always-true assertion, swallowed exception, no assertion | Assert an observable outcome or delete the test |
| Disabled test without an issue link | Fix it, or link the issue |
| `if` / loops in the test body | Parameterized / table-driven test |
| Asserting log output or string representations | Only when that string *is* the product (CLI output, generated SQL, serialized JSON) |
| Enforced test ordering | Make each test independent |
| Tests for value types, getters, mapping code, stdlib | Cover them through the behaviour that uses them |
| Test-only production code (`forTests()`, "visible for testing", skip-validation flags) | Test through the real API; fix the design |
| Full application wiring for a unit-level check | Plain constructor test with fakes; narrow integration tests at boundaries |

## Coverage of the contract, not the lines

Per boundary cover: happy path, each domain failure, boundaries (empty, one, many, max), invalid input rejection, idempotency / repeated calls where relevant. Property-style tests (`decode(encode(x)) == x`) for pure algorithms and invariants.
