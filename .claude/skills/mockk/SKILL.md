---
name: mockk
description: MockK usage policy for Kotlin tests (io.mockk 1.14.x) - the last resort after real objects, fakes and stubs. Use when a test genuinely needs a mock - setup for Gradle, `mockk`/`every`/`coEvery`/`returns`/`throws`, argument matchers, `slot`/`capture`, `mockkObject`/`mockkStatic`/`mockkConstructor` and their cleanup, `MockKExtension`, Spring `@MockkBean`, and the anti-patterns (relaxed mocks, `verify`-driven white-box tests, mocking own code) that the `tdd` skill forbids.
---

# MockK — policy & pitfalls

Prerequisites: the `tdd` skill (`../tdd/SKILL.md`, mocking rules in `../tdd/references/mocking.md`) and `kotlin-testing` (`../kotlin-testing/SKILL.md`). Its rule stands: **real object → hand-rolled fake → stub value → MockK**.
This skill exists so that when you *do* reach step 4, the mock is small, explicit, and the test still asserts **outcomes**, not interactions.

## Setup check (run first)

1. Is MockK already on the test classpath? Gradle MCP `inspect_dependencies` on `testRuntimeClasspath` (see `using-gradle`).
2. Java version: MockK attaches its inline agent at runtime. On **JDK 21+** that prints a dynamic-agent-loading warning (and will fail once the JDK disallows it by default) → add `-XX:+EnableDynamicAgentLoading` to the test JVM args: `tasks.withType<Test> { jvmArgs("-XX:+EnableDynamicAgentLoading") }`. This project runs JDK 25, so this is required as soon as MockK is added.
3. Justify the mock: before writing `mockk<T>()`, write the one-line reason next to it (`// mockk: AWS SDK client, final, no interface`). If the reason is "it's easier than a fake", write the fake.

### Wiring

```toml
[versions]
mockk = "1.14.11"
[libraries]
mockk = { module = "io.mockk:mockk", version.ref = "mockk" }
springmockk = { module = "com.ninja-squad:springmockk", version = "5.0.1" }   # only for Spring @MockkBean; 5.x = Boot 4, 4.x = Boot 3
```

```kotlin
testImplementation(libs.mockk)
```

## MUST DO

- **Strict mocks only: `mockk<T>()`** — never `relaxed = true` / `relaxUnitFun = true` outside the single case of a wide logging/metrics port. Relaxed mocks return `0`/`""`/`null`-ish defaults and hide missing stubs behind green tests.
- **Stub only the *input* side** (`every { gateway.charge(any()) } returns ChargeResult.Declined("insufficient") `) and assert on the subject's **outcome** (return value / state / fake). A mock is a *data source*, not an oracle.
- **Mock the interface you own, not the vendor type.** Wrap `S3Client`/`Stripe`/`HttpClient` in a 1-method port (`interface ObjectStore { fun put(key: String, bytes: ByteArray) }`) — then a fake is trivial and MockK becomes unnecessary; if it must stay, mock the port.
- **`coEvery`/`coVerify` for `suspend` functions**; `every` on a suspend function fails at runtime with an obscure message.
- **Precise matchers.** Prefer exact values (`every { repo.find(UserId(1)) }`) over `any()`. Use `match { it.amount > 0 }` when the argument is derived; `eq()` for value classes; `ofType<T>()` for sealed variants.
- **Clean up global mocks.** `mockkStatic`/`mockkObject`/`mockkConstructor` leak across tests — pair with `unmockkAll()` in `@AfterEach`, or use `@ExtendWith(MockKExtension::class)` which does it for you. Never leave a static mock alive at class end.
- **One mock per external boundary per test.** More than two `mockk<…>()` in one test class is a design smell — introduce a fake or split the subject.

## MUST NOT DO

- **No `verify { … }` as the primary assertion.** `verify(exactly = 1) { repo.save(any()) }` tests the implementation, not behaviour; refactoring to `saveAll` breaks it while behaviour is intact. Assert what the user observes instead. Allowed only when the *call is the product* and no fake is feasible - and then assert the **argument** (`verify { bus.publish(OrderPaid(orderId)) }`), never just "it was called".
- **No `confirmVerified`, `verifyOrder`, `verifySequence`, `verifyAll`** — pure white-box; forbidden by `tdd`.
- **No mocking of your own classes**: data/value classes, domain entities, services, repositories with interfaces, `Clock`, `Random`. Build them or write a fake.
- **No `spyk(subjectUnderTest)`** to stub part of the subject — that tests a Frankenstein object. Extract the collaborator instead.
- **No `mockkStatic`/`mockkConstructor`/`mockkObject` in new code** — they exist for legacy code with hidden dependencies (`LocalDate.now()`, `object` singletons, `new` inside a method). The fix is to inject the dependency (`Clock`, a factory port); use the static mock only as a *temporary* bridge and leave a `TODO` with the injection plan.
- **No `answers { }` that re-implements logic** (`answers { firstArg<Int>() * 2 }`) — if the collaborator's behaviour matters that much, it needs a fake with a real implementation.
- **No `every { … } returns mockk()`** (mocks returning mocks) — the chain hides the shape of data; return real values.
- **No `@MockkBean` for beans you own** - every `@MockkBean` adds a new application context. The rule and the fake-bean alternative live in `spring-testing` ("Keep the context count minimal", MUST NOT DO).
- **No `clearAllMocks()` between tests as a fixture strategy** — recreate mocks per test (plain `private val gateway = mockk<Gateway>()` in the class body; JUnit 6 creates a new instance per test method).

## SHOULD DO

- **Prefer `returnsMany` / `andThen` for sequences** (`every { feed.next() } returnsMany [Tick(1), Tick(2)]`) over stateful `answers`.
- **`throws` for failure reactions**: `every { gateway.charge(any()) } throws GatewayUnavailable()` → assert the order stays `Pending` and the sealed result is `PaymentDeferred`.
- **Capture with `slot()`/`mutableListOf()` only to assert on a derived argument** that no return value exposes (`val sent = slot<Mail>(); every { mailer.send(capture(sent)) } just Runs; …; assertEquals(Email("a@b.c"), sent.captured.to)`). Prefer a `RecordingMailer` fake — it does the same without MockK.
- **`just Runs`** for `Unit` functions that must be stubbed on a strict mock (`every { audit.log(any()) } just Runs`) — this is the explicit alternative to `relaxUnitFun`.
- **Coroutines:** `coEvery { repo.find(id) } returns user`; `coEvery { … } coAnswers { delay(10); … }` only under `runTest`.
- **Use `MockKExtension` + `@MockK` fields** only when a class has ≥ 2 mocks and cleanup matters; otherwise inline `mockk<T>()` reads better.
- **Value classes**: MockK 1.14+ handles `@JvmInline value class` in return types and matchers; match with `eq(UserId(1))` and keep mockk ≥ 1.14.6 if you see `ClassCastException` around boxing.

## SHOULD NOT DO

- **Don't `verify` and `assert` the same fact twice** (result *and* the call that produced it) — one is redundant and one is white-box.
- **Don't mock `List`/`Map`/`Flow`/`Result`/`LocalDate`** — instantiate them (`flowOf(a, b)`, `listOf()`, `LocalDate.of(...)`).
- **Don't put stubs in `@BeforeEach` "for all tests"** — each test declares the stubs it depends on so the behaviour under test is readable in one screen.

## Canonical shape (a justified mock)

```kotlin
class PaymentServiceTest {
    private val orders = InMemoryOrderRepository()                       // fake, we own the interface
    private val gateway = mockk<PaymentGateway>()                        // mockk: vendor SDK adapter, no offline mode
    private val service = PaymentService(orders, gateway, Clock.fixed(T0, UTC))

    @Test
    fun `keeps order pending and reports deferral when gateway is unavailable`() {
        orders.save(anOrder(id = OrderId(7), status = Pending))
        every { gateway.charge(any()) } throws GatewayUnavailable()

        val result = service.pay(OrderId(7))

        assertIs<PaymentResult.Deferred>(result)
        assertEquals(Pending, orders.get(OrderId(7)).status)               // outcome, not `verify`
    }
}
```

## Pitfalls (symptom → fix)

| Symptom | Cause / fix |
|---|---|
| `MockKException: no answer found for …` | Strict mock, missing `every`. Add the stub *in this test*; don't switch to `relaxed` |
| `every` on a suspend fun → `NullPointerException`/`ClassCastException` | Use `coEvery`/`coVerify` |
| `Missing mocked calls inside every { ... } block` | Stubbing a real object, a `final` Java class without inline agent, or an extension function (mock the receiver's member or `mockkStatic("pkg.FileKt")` as a last resort) |
| Test passes alone, fails in suite | Leaked `mockkStatic/Object/Constructor` — `unmockkAll()` in `@AfterEach` or `MockKExtension` |
| Agent warning / failure on JDK 21+ | `-XX:+EnableDynamicAgentLoading` in test JVM args |
| Matcher `any()` ambiguity with overloads | `any<Type>()` or `ofType<Type>()` |
| Mock of `data class` returns wrong `equals` | You mocked a value type — construct it instead |
| Spring: two beans of the same type / `@MockkBean` not applied | `springmockk` major must match Spring Boot (5.x for Boot 4, 4.x for Boot 3); prefer `@TestConfiguration` fake |
| Kotlin `object` behaves oddly after `mockkObject` | Not unmocked; also the object's `init` already ran — prefer injecting an interface |
| `verify` fails after refactor while behaviour is intact | You wrote a white-box test — replace `verify` with an outcome assertion or a fake |
