# DSL, DAO, transactions, and operational patterns

Examples use Exposed's v1 JDBC API unless explicitly marked R2DBC. Schema creation and connection configuration are separate concerns: apply migrations before running this code.

## DSL: explicit SQL and immutable results

```kotlin
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.core.dao.id.LongIdTable
import org.jetbrains.exposed.v1.jdbc.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

object Customers : LongIdTable("customers") {
    val email = varchar("email", 320).uniqueIndex()
    val displayName = varchar("display_name", 200)
}

data class CustomerView(val id: Long, val email: String, val displayName: String)

class CustomerQueries(private val db: Database) {
    fun create(email: String, name: String): Long = transaction(db) {
        Customers.insertAndGetId {
            it[Customers.email] = email
            it[Customers.displayName] = name
        }.value
    }

    fun find(id: Long): CustomerView? = transaction(db) {
        Customers.select(Customers.id, Customers.email, Customers.displayName)
            .where { Customers.id eq id }
            .singleOrNull()
            ?.let { CustomerView(it[Customers.id].value, it[Customers.email], it[Customers.displayName]) }
    }

    fun rename(id: Long, name: String): Boolean = transaction(db) {
        Customers.update({ Customers.id eq id }) {
            it[Customers.displayName] = name
        } == 1
    }

    fun delete(id: Long): Boolean = transaction(db) {
        Customers.deleteWhere { Customers.id eq id } == 1
    }
}
```

These standalone methods each own a transaction. For an atomic operation spanning multiple repository calls, move transaction ownership to the service and let repository operations participate; do not accidentally commit each step independently.

- Use `select(columns).where { ... }` or `selectAll().where { ... }`; old `slice(...).select { ... }` examples predate current APIs.
- Push filtering, ordering, aggregation, and limits into SQL. Add a stable tie-breaker to pagination; prefer keyset pagination for large/changing data sets.
- Use explicit join conditions when several foreign keys could match. Alias tables for self-joins and read result columns through the corresponding alias.
- `batchInsert` avoids a loop of separate application inserts. Generated-value retrieval and driver batch rewriting affect performance; do not promise one network round-trip. Set `shouldReturnGeneratedValues = false` only when those values are unnecessary.
- `upsert`, `returning`, JSON, arrays, and locking vary by dialect/version. Define conflict keys explicitly where supported and test the generated SQL on the target database.
- Returning statements can be lazy: consume them inside the transaction to execute them. Do not assume building a statement executes it.
- `replace` can delete and reinsert a row; it is not an interchangeable update and may trigger cascading effects or reset omitted columns.

## DAO: entity-oriented JDBC access

Add `exposed-dao`. Reuse `Customers` and `CustomerView` above:

```kotlin
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.LongEntity
import org.jetbrains.exposed.v1.dao.LongEntityClass
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class CustomerEntity(id: EntityID<Long>) : LongEntity(id) {
    companion object : LongEntityClass<CustomerEntity>(Customers)

    var email by Customers.email
    var displayName by Customers.displayName
}

fun createCustomer(db: Database, email: String, name: String): CustomerView = transaction(db) {
    val customer = CustomerEntity.new {
        this.email = email
        displayName = name
    }
    CustomerView(customer.id.value, customer.email, customer.displayName)
}
```

- `findById(id)` returns a nullable entity; indexed `EntityClass[id]` expects a row. Use `find { ... }` for predicates, property assignment for updates, and `entity.delete()` for deletion.
- Use matching `IntIdTable`/`IntEntity`, `LongIdTable`/`LongEntity`, UUID, or composite ID types. Plain DSL tables do not need an entity class.
- DAO entities are persistence objects, not domain DTOs. Do not serialize them directly, cache them globally, or move them between databases/transactions.
- DAO writes may be deferred until flush/commit. When mixing DSL mutations and already-loaded entities, deliberately flush/refresh as required by the selected version; test the result in a fresh transaction rather than trusting cached properties.

### DAO on R2DBC (experimental, 1.6.0+)

Requires `exposed-dao-r2dbc` + `exposed-r2dbc`. The API is documented as subject to change or removal; confirm it is acceptable before choosing it, and prefer DSL + R2DBC otherwise. It is **not** a drop-in port of JDBC DAO:

| Aspect | JDBC DAO | R2DBC DAO |
|---|---|---|
| Entity imports | `org.jetbrains.exposed.v1.dao.*` | `org.jetbrains.exposed.v1.dao.r2dbc.*` (`IntEntity`, `IntEntityClass`, `EntityHook`, ...) |
| Transaction | `transaction { }` | `suspendTransaction { }` |
| Query results | `SizedIterable` (an `Iterable`) | `SizedIterable` extending `Flow`; call `.toList()` before `sortedBy`/`map` |
| Create | `new { }`; reading `id` flushes implicitly | `new { }` schedules the insert; reading generated values before a flush throws. Use `newSuspend { }` or `flushCache()` first |
| Update outside its original transaction | Entity re-registers automatically | Setters cannot suspend: call `Companion.attach(entity)` first (`force = true` discards unflushed changes of a different cached instance) |

- Several `new` calls on one table are batched on `flushCache()`, the next statement, or commit; `newSuspend` flushes pending inserts and starts a new batch.
- Do not combine `exposed-dao` and `exposed-dao-r2dbc` in one source set: shared simple names (`Entity`, `EntityClass`, `EntityCache`) collide.
- `detach()` was removed from the R2DBC DAO before release (1.6.0); do not rely on it from older snippets.
- Pending-write caveats from JDBC DAO apply: flush before DSL reads that must see entity writes, and verify persistence in a fresh transaction.

1.6.0 JDBC DAO fixes worth knowing when debugging older behaviour: an entity deleted before its flush is no longer inserted; a cached entity is no longer used when a reference column is declared with `.references()`; and (1.5.0) DAO updates are scheduled independently of the entity cache.

Other 1.5.0/1.6.0 data-access changes: multi-row single-statement `batchInsert` now covers all databases/drivers (1.5.0), `batchInsert` returns only rows actually inserted, column defaults are bound in batch rows that omit them (1.6.0), a nullable column `transform` may now yield a non-null type (1.6.0), and `SELECT` without a `FROM` table is supported (1.6.0). Retest custom batch/transform code after upgrading across these versions.

### Relationships and loading

| Schema | DAO property |
|---|---|
| `reference("customer_id", Customers)` | `var customer by CustomerEntity referencedOn Orders.customer` |
| `optReference("customer_id", Customers)` | `var customer by CustomerEntity optionalReferencedOn Orders.customer` |
| Reverse one-to-many | `val orders by OrderEntity referrersOn Orders.customer` |
| Reverse one-to-one | `backReferencedOn`; enforce uniqueness on the FK in the schema |
| Many-to-many join table | `via`; give the join table a composite key/unique constraint |

The table assumes corresponding `Orders`/`OrderEntity` definitions; it illustrates mappings, not a standalone program.

References are lazy. Use `entity.load(Entity::relation)` for an entity and `.with(Entity::relation)` for collections when navigation would cause N+1 queries. Eager loading can issue multiple batched queries; it is not a guarantee of a single join. Prefer a DSL projection for read-only screens/reports. Materialize the required graph into DTOs before returning.

## Transaction boundaries and coroutines

| Transport | Transaction entry point | Result consumption |
|---|---|---|
| JDBC | `org.jetbrains.exposed.v1.jdbc.transactions.transaction` | Synchronous/blocking; materialize inside the block |
| JDBC with suspend work (current API) | `org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction` | Still blocking database I/O; use an appropriate bounded dispatcher/context |
| R2DBC | `org.jetbrains.exposed.v1.r2dbc.transactions.suspendTransaction` | Non-blocking driver; use R2DBC DSL imports and collect flows inside the block |

Older releases use `newSuspendedTransaction` in an experimental package; verify the pinned version before replacing imports. Never import the JDBC and R2DBC transaction functions indiscriminately.

- Create/reuse the database handle and pool once, not per query. Explicitly pass `db` in multi-database applications; implicit defaults can target the most recently connected database.
- Do not run JDBC on an event-loop thread. Merely wrapping it in a suspend function does not change its driver. A Spring thread-bound transaction needs special care when changing coroutine contexts.
- A lazy query or flow escaping a transaction is not a detached result. Read BLOB/text values before closing the transaction; `text(..., eagerLoading = true)` can help with text fields but does not make arbitrary lazy queries safe outside transactions.
- Nested Exposed transaction blocks share the outer resources by default. `useNestedTransactions = true` uses savepoints, not an independently committed transaction; an outer rollback still rolls everything back. Do not confuse this with Spring `REQUIRES_NEW`.
- Set isolation, timeouts, and retries deliberately. JDBC isolation constants come from `java.sql.Connection`; R2DBC uses `io.r2dbc.spi.IsolationLevel`. Driver support varies.
- `maxAttempts > 1` can replay the transaction body. Keep network calls, emails, and non-idempotent side effects outside retryable work; publishing after commit alone is not guaranteed delivery, so use an outbox when required.
- For concurrent updates, use a version predicate and check the affected row count, database constraints, or explicit locking as appropriate. A preceding existence check does not prevent a race.

## Schema and security practices

- Give durable tables, columns, indexes, and constraints stable names; a Kotlin rename must not silently imply dropping persisted data.
- Model nullable columns explicitly. Distinguish application `clientDefault` values from database defaults used by other writers and migrations.
- Use fixed-precision decimal types for money; choose the date/time module and timezone semantics deliberately. Store a stable representation for enums rather than coupling persisted values to enum order.
- Bind values through the DSL/prepared statements. SQL identifier selection cannot be made safe merely by parameter binding; use an allowlist for dynamic sort/table names.
- Log SQL only where appropriate, with sensitive values protected. Inspect query plans/indexes before adding caches or assuming ORM overhead is the bottleneck.

## Tests worth writing

Prefer a disposable instance of the production database (for example Testcontainers). H2 is useful for lightweight checks but its compatibility modes do not prove PostgreSQL/MySQL semantics.

1. Apply committed Flyway migrations, then test CRUD and DTO mapping through the real repository.
2. Cover unique/FK violations, nulls/defaults, missing rows, rollback, and affected-row counts.
3. Verify DAO persistence in a **fresh transaction**, so cached entities cannot conceal missing writes.
4. Exercise concurrent updates/retry behavior where invariants require it, and verify side effects are not duplicated.
5. Test joins, pagination order, custom types, and dialect-specific SQL on the target engine.
6. Test both fresh installation and upgrade with existing rows for each migration. Ensure unrelated tables/data remain untouched.

Do not mock Exposed's DSL, entity companions, or transaction internals to claim database correctness. Use fakes at a repository boundary for higher-level unit tests, plus real integration tests for persistence.

## Sources

- [DSL CRUD](https://www.jetbrains.com/help/exposed/dsl-crud-operations.html)
- [DSL querying](https://www.jetbrains.com/help/exposed/dsl-querying-data.html)
- [DAO CRUD](https://www.jetbrains.com/help/exposed/dao-crud-operations.html)
- [DAO relationships and eager loading](https://www.jetbrains.com/help/exposed/dao-relationships.html)
- [Transactions](https://www.jetbrains.com/help/exposed/transactions.html)
- [Migration guide: v1 packages and changed APIs](https://www.jetbrains.com/help/exposed/migration-guide-1-0-0.html)