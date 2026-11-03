# Spring Boot integration

The Exposed starters integrate **JDBC** with Spring's datasource and imperative transaction infrastructure. They are not an R2DBC/DAO bridge. For a reactive application, choose the R2DBC DSL and verify its transaction integration separately.

## Match the Spring major

All coordinates below use group `org.jetbrains.exposed` and the same pinned Exposed version.

| Platform | Starter | Transaction module | Exposed auto-configuration class |
|---|---|---|---|
| Spring Boot 3 / Spring Framework 6 | `exposed-spring-boot-starter` | `spring-transaction` | `org.jetbrains.exposed.v1.spring.boot.autoconfigure.ExposedAutoConfiguration` |
| Spring Boot 4 / Spring Framework 7 | `exposed-spring-boot4-starter` | `spring7-transaction` | `org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration` |

The corresponding manager packages are `org.jetbrains.exposed.v1.spring.transaction.SpringTransactionManager` and `org.jetbrains.exposed.v1.spring7.transaction.SpringTransactionManager`. The starter brings the matching integration and `spring-boot-starter-jdbc`; do not add the other major's module. Inspect resolved dependencies before adding optional DAO/date/JSON modules and align them to the same Exposed version.

The reviewed docs require JDK 17+ and list Kotlin 2.1.x; use the compatibility requirements of the actual Exposed, Boot, Kotlin, and build-tool versions together rather than downgrading a newer project. They also warn that Boot 3 support will be removed in the next major Exposed release; recheck this before upgrading Exposed across majors.

Gradle Kotlin DSL, inside an existing Boot build:

```kotlin
val exposedVersion = "1.6.0" // Reviewed documentation baseline; match the project's version.

dependencies {
    implementation("org.jetbrains.exposed:exposed-spring-boot4-starter:$exposedVersion")
    // Boot 3 instead: exposed-spring-boot-starter (not both).
    // Optional: implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    runtimeOnly("org.postgresql:postgresql") // Version managed by the Boot BOM.
}
```

Maven equivalent (with Boot dependency management, and `exposed.version` defined in properties):

```xml
<dependency>
    <groupId>org.jetbrains.exposed</groupId>
    <artifactId>exposed-spring-boot4-starter</artifactId>
    <version>${exposed.version}</version>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

## Configure the right transaction manager

The documented configuration explicitly imports Exposed and excludes Boot's default datasource transaction-manager auto-configuration. Boot 4:

```kotlin
import org.jetbrains.exposed.v1.spring.boot4.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration

@SpringBootApplication
@ImportAutoConfiguration(
    value = [ExposedAutoConfiguration::class],
    exclude = [DataSourceTransactionManagerAutoConfiguration::class]
)
class Application
```

For Boot 3, change **both** imports:

```kotlin
import org.jetbrains.exposed.v1.spring.boot.autoconfigure.ExposedAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
```

Do not exclude datasource auto-configuration itself: Exposed still needs that datasource. Avoid an unrelated second `Database.connect()` against a different connection/pool when Spring is meant to own transactions.

In `src/main/resources/application.properties`:

```properties
spring.datasource.url=${DB_URL}
spring.datasource.username=${DB_USER}
spring.datasource.password=${DB_PASSWORD}
spring.exposed.generate-ddl=false
spring.exposed.show-sql=false
```

Use the project's secret-management mechanism for values. `spring.exposed.generate-ddl=true` is for disposable/demo schemas, not a production Flyway workflow. `spring.exposed.excluded-packages` controls exclusions from that automatic schema scanning; it is not a Flyway filter.

## Service boundaries

- Use `org.springframework.transaction.annotation.Transactional` on Spring-managed service methods/classes. Exposed DSL/DAO operations inside those methods do **not** need an extra `transaction {}` block.
- Ensure Kotlin Spring proxy support is configured (normally the Kotlin Spring compiler plugin). Self-invocation bypasses proxy interception; private/non-proxyable methods do not create the transaction you expect.
- Map DAO entities/lazy results to DTOs before the transactional method returns. A controller should not trigger lazy database reads while serializing a response.
- Spring rollback rules apply: by default runtime exceptions roll back, checked exceptions do not. Specify `rollbackFor` when required and test the desired behavior.
- When several managers exist, select the actual Exposed manager bean with `@Transactional(transactionManager = "...")`; configure `@Primary` deliberately if a default is needed. Do not assume a JPA/plain JDBC manager establishes Exposed's transaction context.
- Customize Exposed through a `DatabaseConfig` bean when necessary. Savepoints, timeout, and retry settings are operational choices, not boilerplate to enable indiscriminately.
- Do not assume a suspend service method plus this JDBC starter creates a non-blocking/reactive transaction. Verify context propagation before changing threads within a Spring-managed operation.

## Flyway and native images

Add Flyway runtime support separately, including its database-specific module when required by the chosen Flyway version. For Boot 4, check the dedicated `spring-boot-starter-flyway`; Boot 3 commonly uses `org.flywaydb:flyway-core` with Boot dependency management. Verify the Boot version's auto-configuration requirements rather than treating Exposed's starter as a Flyway starter.

Generate SQL with the [Exposed build plugins](migrations.md), review/commit it, and let the application's Flyway integration or deployment pipeline apply it. Confirm migrations finish before application queries run, especially with custom startup code or multiple datasources.

The Exposed starter contributes GraalVM runtime hints. Native/AOT builds can still need application-specific reflection hints; diagnose missing types rather than globally enabling reflection. The docs warn that changing `spring.exposed.generate-ddl` at native-image runtime cannot activate an AOT-excluded bean. Prefer migrations over trying to use runtime schema auto-creation as deployment infrastructure.

## Sources

- [Exposed Spring Boot integration](https://www.jetbrains.com/help/exposed/spring-boot-integration.html)
- [Official Boot 4 application example](https://github.com/JetBrains/Exposed/blob/main/documentation-website/Writerside/snippets/exposed-spring/src/main/kotlin/com/example/exposedspring/ExposedSpringApplication.kt)
- [v1 migration guide, including Spring 6/7 split](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)
- [Boot 4 JDBC transaction auto-configuration API](https://docs.spring.io/spring-boot/4.0/api/java/org/springframework/boot/jdbc/autoconfigure/DataSourceTransactionManagerAutoConfiguration.html)
- [Spring Boot database initialization and Flyway](https://docs.spring.io/spring-boot/how-to/data-initialization.html)