# Mocking - good vs bad

Examples below are language-neutral pseudocode. Stack-specific renderings live in the stack skills (e.g. `kotlin-testing` → `references/examples.md`, mocking library rules in `mockk`, framework wiring in `spring-testing`).

## When to mock

Mock only at system boundaries:
- external APIs (external systems: payment gateway, mail provider, cloud SDKs)
- databases (sometimes - prefer real databases in containers or an in-memory fake of the repository port)
- Time (substitute via an injected clock set to a fixed instant - not a mock; async code → virtual time)

Don't mock:
- Classes / Modules we own (services, repositories behind an interface, domain entities, value types)
- Types that are easy to instantiate (collections, dates, streams, result types, DTOs) - build the real value
- The subject under test (partial mock / spy) - that tests a Frankenstein object, not the code

## Substitution order

When a collaborator is inconvenient, go down this list and stop at the first that works:

1. **Real thing** - pure functions, in-process components, in-memory DB / containers, fixed clock, test scheduler, seeded random.
2. **Hand-rolled fake** implementing the collaborator's interface (`InMemoryUserRepository`, `RecordingMailer`, `FailingGateway`) - ~20 lines, reused across tests, behaves like the real one (uniqueness, ordering …).
3. **Stub value** - an object/function returning canned data, for read-only ports.
4. **Only then a mocking library** - justify it on the same line (`// mock: vendor SDK adapter, no offline mode`). If the reason is "it's easier than a fake", write the fake.

## Do mocking correctly

At boundaries, design implementations that are easy to substitute.

- **Use Dependency Injection at boundaries** - constructor-inject ports (`Mailer`, `PaymentGateway`, `Clock`, `IdGenerator`); never read wall-clock time, generate random ids or instantiate an external client inside the subject.
- **Own the interface you substitute.** Wrap vendor types (cloud storage client, payment SDK, HTTP client) in a thin port (`ObjectStore.put(key, bytes)`). The fake becomes trivial and a mock usually unnecessary.
- **Substitute the input side, assert the outcome.** A mock/stub is a *data source*, not an oracle: stub what the collaborator returns/raises, then assert the subject's return value, resulting state, or what a *fake* recorded.
- **Fakes live in the test source set** (a `fakes` package), named `InMemoryX` / `RecordingX` / `FixedX` / `FailingX`, exposing what tests need (`sent: List<Mail>`). If the real adapter has a contract test, run the same abstract contract test against fake and real.
- **Framework wiring:** substitute only true external systems, by registering fakes in the test configuration; don't replace half the application with mocks.

## Good vs bad

### Interaction assertions vs outcome assertions

Bad - white-box, breaks when `save` becomes `saveAll` although behaviour is intact:

```
test "registers user":
    repo = mock(UserRepository, lenient)
    mailer = mock(Mailer, lenient)
    service = RegistrationService(repo, mailer, SystemClock())

    service.register(email = "a@b.c", name = "Ada")

    verify repo.save(any) called exactly once
    verify mailer.send(any) called
```

Good - fakes we own, assertions on observable outcomes:

```
test "registers a new user and sends a welcome mail":
    users = InMemoryUserRepository()
    mailer = RecordingMailer()
    service = RegistrationService(users, mailer, FixedClock(T0))

    result = service.register(email = "a@b.c", name = "Ada")

    assert result is Registered
    assert users.findByEmail("a@b.c") == result.user
    assert mailer.sent == [WelcomeMail(to = "a@b.c")]
```

### Fake vs mock for a collaborator we own

Bad - mocking our own repository, the stub re-implements its logic:

```
repo = mock(OrderRepository)
when repo.find(id) answer { orders[id] }
when repo.save(order) answer { orders[order.id] = order; order }
```

Good - a fake with a real implementation:

```
class InMemoryOrderRepository implements OrderRepository:
    orders = map()
    find(id) = orders[id]
    save(order) = orders[order.id] = order; return order
```

### Failure reaction at a real boundary (justified mock)

Acceptable - vendor adapter, strict mock, input side stubbed, outcome asserted:

```
test "keeps order pending when gateway is unavailable":
    orders = InMemoryOrderRepository()          // fake, we own the interface
    gateway = mock(PaymentGateway)              // mock: vendor SDK adapter, no offline mode
    service = PaymentService(orders, gateway, FixedClock(T0))
    orders.save(anOrder(id = 7, status = Pending))
    when gateway.charge(any) raise GatewayUnavailable

    result = service.pay(7)

    assert result is Deferred
    assert orders.find(7).status == Pending     // outcome, not verify
```

Even better when the gateway has few modes - a 5-line `FailingGateway` fake, no mocking library at all.

### Time

Bad - globally mocking the static "now" function (leaks across tests, hides the dependency).

Good - inject a clock; in tests use a fixed clock; advance with a mutable clock fake when needed.

## When a mock is genuinely justified

- Third-party SDK with a huge/final API and no interface (cloud SDK clients) → mock the *thin adapter interface you wrote around it*, not the SDK itself. If even that is impossible, mock the SDK with stubs for the **input** side only; still assert on outcomes.
- Verifying a failure *reaction* ("when the gateway raises, the order stays `Pending`") → a `FailingGateway` fake is 5 lines; a mock that raises is acceptable if the fake would need many modes.
- In all cases: keep the mock **strict** (no lenient/default answers), stub only what the test needs, and never verify calls unless the call itself *is* the product (e.g. "publishes exactly one event" where the bus is external) — and even then prefer a `RecordingEventBus`.

## Mocking anti-patterns (symptom → fix)

| Anti-pattern | Why it's bad | Fix |
|---|---|---|
| Call verification / call order as the assertion | Tests how, not what; breaks on refactor | Assert return value, state, or a recording fake |
| Lenient mocks returning defaults | Hide missing stubs behind green tests | Strict mocks, stub what the test needs |
| Mocking own classes / value types | Couples test to internals; mock equality lies | Construct the value or write a fake |
| Mocks returning mocks | Hides the shape of the data | Return real values |
| Stub answers re-implementing logic | Duplicates the collaborator in the test | Fake with a real implementation |
| Mocking statics / singletons / constructors | Global, leaks between tests, hides a missing dependency | Inject the dependency (clock, factory port) |
| Stubs in shared setup "for all tests" | Behaviour under test not readable in one screen | Each test declares its own stubs |
| > 2 mocks in one test class | Subject has too many boundaries | Introduce fakes or split the subject |
| Verifying through a side-channel (raw DB query, log output) | Bypasses the boundary under test | Read back through the public API |
