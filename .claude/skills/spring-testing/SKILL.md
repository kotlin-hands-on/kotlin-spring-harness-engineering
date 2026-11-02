---
name: spring-testing
description: Spring Boot testing policy (Boot 4, Kotlin). Use when writing, reviewing or fixing Spring tests - keeping the number of application contexts minimal (context caching, one shared test configuration, no `@MockitoBean`/`@MockkBean`/`@DirtiesContext`), testing through any public API (service, HTTP via MockMvc/RestTestClient, Kafka, repository), substituting external systems with `@TestConfiguration` fakes, Testcontainers via `@ServiceConnection`, test isolation in a shared context, and overriding the Boot-managed JUnit version. Applies the `tdd` principles; Kotlin idioms in `kotlin-testing`.
---

# Spring Boot testing

Prerequisites: `tdd` (`../tdd/SKILL.md`) for the principles, `kotlin-testing` (`../kotlin-testing/SKILL.md`) for the JUnit 6 / kotlin.test stack. This skill only adds what Spring changes.

The one Spring-specific rule that matters most: **every distinct test configuration costs a new application context.** Spring caches contexts by their configuration key; tests that share the key reuse a running context, tests that don't pay a full startup (plus containers, plus connection pools). Mocking beans is the most common way to break the key - so avoiding mocks is both the `tdd` policy and the performance policy.

## Setup check (run first)

1. **JUnit version under Boot dependency management.** The Spring Boot BOM pins `junit-jupiter.version`; override it instead of adding a second BOM:

   ```kotlin
   extra["junit-jupiter.version"] = "6.1.3"

   dependencies {
       testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")   // per-starter test modules (Boot 4)
       testImplementation("org.springframework.boot:spring-boot-testcontainers")
       testImplementation("org.junit.jupiter:junit-jupiter")
       testImplementation(kotlin("test"))
       testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")    // needed for `@Test suspend fun`
       testRuntimeOnly("org.junit.platform:junit-platform-launcher")
   }
   ```

   Verify with the Gradle MCP `inspect_dependencies` on `testRuntimeClasspath` (see `using-gradle`) - Jupiter and Platform must resolve to the same `6.x`.
2. **Existing shared test setup?** Look in `src/test` for a shared integration-test annotation/base class, `@TestConfiguration` classes and fakes. **Reuse them** - a new test class with its own configuration is a new context.

## Which public API to test through

Any public boundary of the application is a valid entry point - pick the one where the behaviour is observable. Act and assert through public APIs; don't reach into internals or verify interactions.

| Behaviour | Entry point |
|---|---|
| Pure domain / service logic with no Spring wiring | Plain constructor test with fakes - **no context at all** (cheapest) |
| Use case wired through Spring (transactions, events, security, validation) | Shared `@SpringBootTest` context, call the service bean's public methods |
| HTTP contract (status, JSON, validation, error mapping) | Shared `@SpringBootTest` context + `MockMvc` / `RestTestClient` |
| Messaging | Shared context; publish to the input topic / call the public producer, observe the effect via another public API or the output topic (Awaitility for async) |
| Persistence mapping / custom queries | Shared context (or `@DataJpaTest`/`@DataJdbcTest`, see below), call the repository; reading back through the same repository is fine |

Whatever the entry point: **state set up and checked through public APIs** of the application (service, repository, endpoint, topic) - not through `JdbcTemplate` side channels that bypass the mapping under test.

## Keep the context count minimal

Spring's context cache key includes: configuration classes and `@Import`s, active profiles, `@TestPropertySource`/`properties`, `@DynamicPropertySource` methods, web environment, context customizers - **including every `@MockitoBean`/`@MockitoSpyBean`/`@MockkBean` field**. Change any of them and the test class gets its own context.

1. **One shared integration-test entry point** - a meta-annotation (or abstract base class) every Spring test uses unchanged:

   ```kotlin
   @Target(AnnotationTarget.CLASS)
   @Retention(AnnotationRetention.RUNTIME)
   @SpringBootTest
   @AutoConfigureMockMvc
   @ActiveProfiles("test")
   @Tag("integration")
   @Import(TestcontainersConfig::class, FakeExternalSystemsConfig::class)
   annotation class IntegrationTest
   ```

   ```kotlin
   @IntegrationTest
   class PlaceOrderTest(@Autowired val orders: OrderService, @Autowired val mailer: RecordingMailer) { ... }
   ```

2. **Containers as `@ServiceConnection` beans** in the shared configuration, so they live as long as the cached context and are shared by all test classes:

   ```kotlin
   @TestConfiguration(proxyBeanMethods = false)
   class TestcontainersConfig {
       @Bean @ServiceConnection fun postgres() = PostgreSQLContainer("postgres:17")
       @Bean @ServiceConnection fun kafka() = KafkaContainer("apache/kafka:4.0.0")
   }
   ```

   Not per-class `@Container` + `@DynamicPropertySource` - each class then gets new properties, a new context and a new container.
3. **Externals replaced once, for everyone** - true external systems (mail, payment, third-party HTTP) get a fake bean in the shared `@TestConfiguration`, used by every test:

   ```kotlin
   @TestConfiguration(proxyBeanMethods = false)
   class FakeExternalSystemsConfig {
       @Bean @Primary fun mailer() = RecordingMailer()
       @Bean @Primary fun clock(): Clock = MutableClock(Instant.parse("2026-01-01T00:00:00Z"))
   }
   ```

   Because the fake is shared, **reset it in `@BeforeEach`** (`mailer.clear()`), and give it controllable behaviour (`paymentGateway.failNext()`) instead of reaching for a per-class mock.
4. **Slices only when they pay off.** Each distinct `@WebMvcTest(X::class)` / `@DataJpaTest` setup is another context. A shared `@SpringBootTest` that is already running is usually cheaper than a dozen slices. Use a slice only when the full context is genuinely heavy and many tests share that exact slice configuration.
5. **Check it:** `logging.level.org.springframework.test.context.cache=DEBUG` logs cache hits/misses and the context count; count Spring Boot banners/startups in the test log. More than a handful of contexts for the suite means configuration drift.

## Isolate state in a shared context

A reused context means reused databases, topics and fake beans - tests must not leak into each other.

- **Unique data per test** (random ids, unique e-mails/order numbers) - the default; tests also stay order-independent and parallel-safe.
- **Clean up explicitly** where uniqueness isn't enough - truncate the affected tables in `@BeforeEach`, reset fakes.
- `@Transactional` on a test only rolls back work done on the test thread - it does **not** cover `RestTestClient` against a real port, `@Async`, or Kafka listener threads, and it hides commit-time behaviour. Prefer unique data.
- Kafka: unique keys or consumer group per test; assert with Awaitility (`await().untilAsserted { ... }`), never `Thread.sleep`.

## MUST NOT DO

- **No `@MockitoBean` / `@MockitoSpyBean` / `@MockkBean`** for beans you own - it creates a new context and turns the test white-box. Last resort only for an outbound port to a third-party system with no feasible fake, and then put it in the **shared** configuration, not on one test class (see `mockk`).
- **No `@DirtiesContext`** - it throws away the cached context; fix the shared state instead.
- **No per-class `@TestPropertySource`, `properties = [...]`, `@ActiveProfiles` variations or extra `@Import`s** - move the setting into the shared configuration, or make the behaviour switchable at runtime through a fake.
- **No `@SpringBootTest` for logic that needs no wiring** - plain constructor test.
- **No side-channel verification** (`JdbcTemplate` counts) instead of the public API.
- **No H2 "compatibility mode" in place of the real database** when query behaviour matters - Testcontainers.

## Good vs bad - one context, no mocks

Bad - per-class mock of an owned bean: new context for this class, interaction-verifying test:

```kotlin
@SpringBootTest
class PlaceOrderTest(@Autowired val orders: OrderService) {
    @MockkBean lateinit var inventory: InventoryService   // own bean -> new context

    @Test
    fun `reserves stock`() {
        every { inventory.reserve(any(), any()) } returns true
        orders.place(order)
        verify { inventory.reserve("sku-1", 2) }
    }
}
```

Good - shared context, real collaborators, outcome observed through public APIs:

```kotlin
@IntegrationTest
class PlaceOrderTest(@Autowired val orders: OrderService, @Autowired val inventory: InventoryService) {
    @Test
    fun `placing an order reserves stock`() {
        val sku = inventory.addStock(sku = "sku-${UUID.randomUUID()}", quantity = 5)

        orders.place(OrderRequest(sku, quantity = 2))

        assertEquals(3, inventory.available(sku))
    }
}
```

## Pitfalls (symptom → fix)

| Symptom | Cause / fix |
|---|---|
| Slow suite, many context startups | Per-class mocks, properties, profiles or imports - move into the shared `@IntegrationTest` configuration, drop `@DirtiesContext` |
| One container per test class | `@Container` + `@DynamicPropertySource` per class - `@ServiceConnection` beans in the shared `@TestConfiguration` |
| Tests pass alone, fail in the suite | Shared state leaks through the cached context - unique data per test, reset fakes in `@BeforeEach` |
| Too many open connections / OOM late in the suite | Many cached contexts alive at once (default max 32) - reduce configuration variants rather than tuning `spring.test.context.cache.maxSize` |
| Suspend tests not run / Jupiter 5.x on classpath | BOM still pins 5.x - set `extra["junit-jupiter.version"]` |
| Two beans of the same type | Fake bean missing `@Primary`, or the production bean is also active - `@Primary` or a profile |
