---
name: exposed-orm
description: JetBrains Exposed SQL/ORM setup and usage for Kotlin. Use when adding or upgrading Exposed, choosing JDBC/R2DBC or DSL/DAO, defining tables and relationships, writing queries and transactions, generating Flyway SQL migrations with the Exposed Gradle or Maven plugins, integrating Spring Boot 3 or 4, or reviewing database performance and persistence tests. Covers dependencies, v1 imports, migration safety, coroutine boundaries, and production patterns; no dedicated Kotlin Toolchain migration plugin is assumed.
---

# Exposed ORM

## Start here

1. Inspect the existing build, Exposed version, database dialect/driver, transaction boundaries, and migration history before changing them.
2. Identify the runtime: JDBC or R2DBC; plain application or Spring (Boot **3 versus 4**); DSL, DAO, or an existing combination. Preserve deliberate project choices.
3. Read only the relevant references below. Verify version-sensitive APIs against official documentation for the project's pinned version, not old blog posts.
4. Implement the smallest change and validate against a real database of the production dialect when SQL/schema behavior matters.

Documentation reviewed **2026-11-03**: the official help pages identify themselves as **Exposed 1.6.0** (released 2026-10-16). Examples target the v1 API. This is a documentation baseline, not an instruction to upgrade every project or an evergreen claim about the latest release. Check plugin availability separately: older Exposed releases do not include both build plugins. 1.6.0 lists no breaking changes or deprecations and no Spring changes; its notable additions are an experimental R2DBC DAO and multi-package support in the migration plugins.

## Reference map

| Task | Read |
|---|---|
| Gradle/Maven dependencies, driver, optional modules, v1 upgrade | [Build setup](references/build-setup.md) |
| Generate SQL with Exposed plugins and apply it with Flyway | [Migrations](references/migrations.md) |
| DSL/DAO examples, relationships, transactions, performance, tests | [Data access](references/data-access.md) |
| Boot 3/4 starters, Spring transaction management, native images | [Spring Boot](references/spring-boot.md) |

## Choose the API and transport separately

| Choice | Appropriate when | Constraints |
|---|---|---|
| DSL + JDBC | Explicit SQL, joins, projections, bulk operations; a straightforward default for new blocking persistence code | JDBC blocks its thread |
| DAO + JDBC | Entity navigation, delegated properties, small aggregate-style CRUD | Requires `exposed-dao`; entities/cache are transaction-bound; watch N+1 queries |
| DSL + R2DBC | An end-to-end non-blocking coroutine application with a supported driver | Use R2DBC query/transaction APIs and collect results inside the transaction |
| DAO + R2DBC | Only when entity navigation is worth an **experimental** API (new in 1.6.0); prefer DSL + R2DBC otherwise | Requires `exposed-dao-r2dbc` (not `exposed-dao`); its API "may be dropped or changed at any time"; different packages and semantics from JDBC DAO, see [Data access](references/data-access.md) |

`exposed-dao` is **JDBC only**; never put it on an R2DBC path, and never combine `exposed-dao` and `exposed-dao-r2dbc` in one source set (they share the simple names `Entity`, `EntityClass`, `EntityCache`).

DSL and DAO can share table definitions on JDBC. Do not introduce DAO just to wrap a reporting query, or replace an established DAO model without a reason. Be deliberate about cache/flush behavior when mixing DSL writes and loaded DAO entities.

## MUST DO

- Align all Exposed library modules to one version; keep Spring major, driver, and plugin compatibility explicit.
- Use `org.jetbrains.exposed.v1.*` imports for Exposed 1.x. Resolve APIs from the correct transport package; do not mechanically add `v1` to every old import.
- Execute and consume queries inside a transaction. Return DTOs/domain values, not lazy `Query`, `SizedIterable`, `Flow`, or DAO entities across service/API boundaries.
- Choose one transaction owner per service operation: Exposed transaction blocks in plain applications, or the Exposed Spring transaction manager with Spring `@Transactional`.
- Keep transactions short. Treat retryable transaction bodies as potentially executed more than once; use an outbox or other explicit strategy for external side effects.
- Generate schema diffs against a disposable database restored to the last committed migration. Review generated SQL, then apply it with Flyway in a separate, controlled step.
- Enforce uniqueness, nullability, foreign keys, and concurrency invariants in the database, not solely in application checks.
- Test persistence behavior using real tables and transactions; test migrations both from empty and from the previous supported schema.

## MUST NOT DO

- Do not treat Exposed's migration plugins as deployment runners: they do not apply newly generated migrations. Testcontainers mode does replay existing Flyway scripts to build a disposable comparison baseline.
- Do not enable automatic schema creation alongside Flyway in production. `SchemaUtils` and `spring.exposed.generate-ddl` are not a substitute for versioned migrations.
- Do not generate against production, accept a generated `DROP` blindly, rewrite an already-applied migration, or infer that a rename is safe from a schema diff.
- Do not assume `suspend` makes JDBC non-blocking, or copy a JDBC DAO sample into R2DBC code (imports, `Flow` results, `newSuspend`, and `attach` all differ).
- Do not install both Boot starters or mix `spring-transaction` with Spring 7 / `spring7-transaction` with Spring 6.
- Do not interpolate untrusted input into raw SQL or identifiers; bind values and allowlist any dynamic identifiers.
- Do not invent a dedicated Exposed Kotlin Toolchain migration plugin or task. Use a supported Gradle/Maven helper for generation when the main project uses that toolchain.

## Completion checklist

- Dependencies and imports match the selected Exposed, transport, and Spring versions.
- Transaction scope, rollback, retry, and result materialization are intentional.
- Query shape is bounded; joins/projections/eager loading avoid accidental N+1 and full-table reads.
- Generated SQL has a unique Flyway name, has been reviewed, and survives an upgrade test without data loss.
- Credentials are externalized; production SQL logging does not leak parameters or personal data.

## Official entry points

- [Exposed overview](https://www.jetbrains.com/help/exposed/about.html)
- [Official source and module list](https://github.com/JetBrains/Exposed)
- [API reference](https://jetbrains.github.io/Exposed/api/)
- [Migration guide to 1.0](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)
- [Changelog](https://github.com/JetBrains/Exposed/blob/main/CHANGELOG.md)

If current help, indexed documentation, and the project's version disagree, consult the matching release/tag and report the mismatch rather than blending APIs.