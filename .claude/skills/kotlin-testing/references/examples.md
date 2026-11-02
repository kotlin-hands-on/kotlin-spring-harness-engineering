# Kotlin examples - good vs bad tests & mocks

Kotlin renderings of the principles in `../../tdd/references/tests.md` and `../../tdd/references/mocking.md`. Read those for the *why*; this file shows the *how* in Kotlin with JUnit 6 + kotlin.test.

## Tests

### Name after behaviour, not method

Bad:

```kotlin
@Test fun testRegister() { ... }
@Test fun register2() { ... }
```

Good - one behaviour per test, the name is the specification:

```kotlin
@Test fun `registers a new user and sends a welcome mail`() { ... }
@Test fun `rejects a duplicate email without sending mail`() { ... }
```

### Implementation coupling vs public boundary

Bad - widens visibility to test an internal helper:

```kotlin
// production: internal fun normalize(email: String) = ...   ← was private, opened for the test
@Test
fun `normalize lowercases`() = assertEquals("a@b.c", service.normalize("A@B.C"))
```

Good - the same behaviour through the boundary:

```kotlin
@Test
fun `treats emails case-insensitively when checking for duplicates`() {
    users.save(aUser(email = Email("a@b.c")))

    val result = service.register(RegisterCommand(email = Email("A@B.C"), name = "Bob"))

    assertIs<RegisterResult.EmailTaken>(result)
}
```

### Tautology vs literal expectation

Bad:

```kotlin
assertEquals(price * VAT_RATE, calculator.gross(price))
```

Good:

```kotlin
assertEquals(119.toBigDecimal(), calculator.gross(100.toBigDecimal()))
```

### Snapshot-everything vs focused assertion

Bad:

```kotlin
assertEquals(Order(id = OrderId(7), status = Paid, createdAt = T0, items = [...], customer = ..., notes = ""), result.order)
```

Good - the smart cast from `assertIs` gives access to the payload:

```kotlin
val paid = assertIs<PaymentResult.Paid>(result)
assertEquals(Paid, paid.order.status)
```

### Shared mutable fixture vs per-test fixture

Bad:

```kotlin
companion object { val users = InMemoryUserRepository() }   // leaks between tests
@BeforeEach fun setUp() { users.save(aUser()) }             // hidden precondition for every test
```

Good - JUnit creates a fresh instance per test method, preconditions visible in *Arrange*:

```kotlin
private val users = InMemoryUserRepository()
private val service = RegistrationService(users, RecordingMailer(), Clock.fixed(T0, UTC))

@Test
fun `rejects a duplicate email`() {
    users.save(aUser(email = Email("a@b.c")))

    val result = service.register(RegisterCommand(email = Email("a@b.c"), name = "Bob"))

    assertIs<RegisterResult.EmailTaken>(result)
}

private fun aUser(email: Email = Email("x@y.z"), name: String = "Ada") = User(email = email, name = name)
```

### Logic in tests vs parameterized table

Bad:

```kotlin
@Test
fun `chooses tier`() {
    for (q in listOf(0, 1, 999, 1000)) {
        if (q == 0) assertEquals(FREE, Pricing.tierFor(q)) else if (q < 1000) assertEquals(STANDARD, Pricing.tierFor(q)) else ...
    }
}
```

Good:

```kotlin
@ParameterizedTest(name = "{0} → {1}")
@CsvSource("0, FREE", "1, STANDARD", "999, STANDARD", "1000, BULK")
fun `chooses shipping tier by quantity`(quantity: Int, expected: Tier) =
    assertEquals(expected, Pricing.tierFor(quantity))
```

### Non-deterministic vs deterministic

Bad - `Thread.sleep(500)`, `Instant.now()` in expectations, `UUID.randomUUID()` inside the subject.

Good - inject `Clock` (`Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), UTC)`), `Random(seed)`, an `IdGenerator` port; coroutines via `runTest` virtual time / `advanceUntilIdle()`.

## Mocks

### Interaction assertions vs outcome assertions

Bad - white-box, breaks when `save` becomes `saveAll` although behaviour is intact:

```kotlin
@Test
fun `registers user`() {
    val repo = mockk<UserRepository>(relaxed = true)
    val mailer = mockk<Mailer>(relaxed = true)
    val service = RegistrationService(repo, mailer, Clock.systemUTC())

    service.register(RegisterCommand(email = Email("a@b.c"), name = "Ada"))

    verify(exactly = 1) { repo.save(any()) }
    verify { mailer.send(any()) }
}
```

Good - fakes we own, assertions on observable outcomes:

```kotlin
@Test
fun `registers a new user and sends a welcome mail`() {
    val users = InMemoryUserRepository()
    val mailer = RecordingMailer()
    val service = RegistrationService(users, mailer, Clock.fixed(T0, UTC))

    val result = service.register(RegisterCommand(email = Email("a@b.c"), name = "Ada"))

    val ok = assertIs<RegisterResult.Registered>(result)
    assertEquals(ok.user, users.findByEmail(Email("a@b.c")))
    assertEquals([WelcomeMail(to = Email("a@b.c"))], mailer.sent)
}
```

### Fake vs mock for a collaborator we own

Bad - mocking our own repository, `answers` re-implements its logic:

```kotlin
val repo = mockk<OrderRepository>()
every { repo.find(any()) } answers { orders[firstArg()] }
every { repo.save(any()) } answers { orders[firstArg<Order>().id] = firstArg(); firstArg() }
```

Good - fakes with a real implementation (in `src/test/kotlin/<pkg>/fakes/`):

```kotlin
class InMemoryOrderRepository : OrderRepository {
    private val orders = mutableMapOf<OrderId, Order>()
    override fun find(id: OrderId): Order? = orders[id]
    override fun save(order: Order): Order = order.also { orders[it.id] = it }
}

class RecordingMailer : Mailer {
    private val _sent = mutableListOf<Mail>()
    val sent: List<Mail> get() = _sent
    override fun send(mail: Mail) { _sent += mail }
}
```

### Failure reaction at a real boundary (justified mock)

Acceptable - vendor adapter, strict mock, input side stubbed, outcome asserted (see the `mockk` skill):

```kotlin
private val orders = InMemoryOrderRepository()                  // fake, we own the interface
private val gateway = mockk<PaymentGateway>()                   // mockk: vendor SDK adapter, no offline mode
private val service = PaymentService(orders, gateway, Clock.fixed(T0, UTC))

@Test
fun `keeps order pending when gateway is unavailable`() {
    orders.save(anOrder(id = OrderId(7), status = Pending))
    every { gateway.charge(any()) } throws GatewayUnavailable()

    val result = service.pay(OrderId(7))

    assertIs<PaymentResult.Deferred>(result)
    assertEquals(Pending, orders.find(OrderId(7))?.status)       // outcome, not `verify`
}
```

Even better when the gateway has few modes - a 5-line `FailingGateway` fake, no MockK at all.

### Time

Bad - `mockkStatic(LocalDate::class)` / `every { LocalDate.now() } returns ...` (global, leaks across tests).

Good - inject `Clock`; in tests `Clock.fixed(...)`; advance with a `MutableClock` fake when needed.
