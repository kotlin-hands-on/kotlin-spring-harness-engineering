# Design

A structural refactor of `:backend` driven by the user's decisions D-1..D-4 — as expanded at the approval gate — and the ADR drafted in this change (`adr.md`, materialized as `docs/adr/0001-vertical-slices.md`). Zero change to the pinned wire behavior: every existing black-box/contract test stays green without assertion edits. The two resolver nuances accepted at the gate are unpinned by tests and specs.

Throughout, `BASE` = `com.example.harnessengineeringspring`.

## D-A Canonical slice anatomy (D-1, expanded at the gate: five parts)

```
BASE/<slice>/                 the slice's public API
  <RootService>.kt            @Transactional use-case orchestrator(s) + facade; the only
                              entry point other slices / web may call
  <value + result types>.kt   public value classes and sealed results — the vocabulary
                              that crosses the slice boundary
  api/                        @RestController, HTTP mapping to ApiException only
  domain/                     non-service machinery: id/token generators, pure logic,
                              POKOs, type hierarchies — internal to the slice
  application/                OPTIONAL internal services; called only by the slice's own
                              root services; never cross-slice, never from api/, domain/,
                              persistence/. Created only when a root service delegates
                              real work to it — NOT created for group/member here.
  persistence/                Exposed table objects + REPOSITORIES (the only query surface)
```

Gate-refined decisions:

- **The root service is the use case** (as `GroupService` is today): `@Transactional`, sealed results, facade to other code. A root package follows the Single Responsibility Principle, so a slice may hold **one or very few** root services when its public API spans genuinely different aspects — "one service per slice" is the default, multiple root services is the sanctioned exception, not a template.
- **`domain/` holds internal machinery** — pure logic, POKOs, type hierarchies. The interpretation recorded at the gate: types that cross the slice boundary (what root services hand out) stay in the **root** per D-2; `domain/` holds types and logic no other slice ever sees. A thin root facade delegating one-to-one to `domain/` use-case classes remains rejected as ceremony.
- **`application/` is optional and internal-only**: its services exist to keep root services focused. Callable only by the slice's own root services — never cross-slice (cross-slice is root-only anyway, and `application/` is a sub-package), never from `api/`, `domain/`, `persistence/`. No `application/` package is created in this change; the ADR records it as allowed-when-needed and the ArchUnit rule guards it from day one.
- **`persistence/` = tables + repositories; repositories are the only query surface.** Root and application services never touch the Exposed DSL or `Table` objects directly — all row access goes through a repository class in the slice's `persistence/`. Repositories are plain Spring beans holding the Exposed queries; they carry no `@Transactional` of their own (the calling service owns the transaction, D-D). `GroupService`'s and `MemberService`'s inline query code migrates into `GroupRepository` / `MemberRepository`, so the slice moves are extract-then-move, not pure file moves.
- **Hashing note**: `GroupRepository.findByTokenHash` needs the token digest. The repository may inject the `domain/GroupTokenGenerator` for hashing (intra-slice `persistence/`→`domain/` is not restricted), or the service may pass the digest in — implementer's choice; nothing in the rules constrains it.

Sealed results follow `kotlin-engineer` policy: only outcomes the service can still produce remain (D-C removes the unreachable `NotFound` variants). Services never throw `ApiException` — that stays in `api/` and `web/`.

## D-B Target layout and file moves

```
BASE/                          HarnessEngineeringSpringApplication.kt (unchanged)
BASE/group/                    GroupService, Group, GroupId, GroupToken, CreateGroup,
                               GroupRename, GroupAccess(+GroupAccessResult),
                               ResolvedGroup (new, D-C)
BASE/group/api/                GroupController
BASE/group/domain/             GroupIdGenerator, GroupTokenGenerator
BASE/group/persistence/        Groups (table), GroupRepository (new: insert, findByTokenHash,
                               updateName — extracted from GroupService)
BASE/member/                   MemberService, MemberId, MemberResults (AddMember, UpdateMember,
                               DeleteMember; MemberList deleted — D-C)
BASE/member/api/               MemberController
BASE/member/domain/            MemberIdGenerator, MemberRow (row POKO the repository produces
                               and the service consumes)
BASE/member/persistence/       Members (table), MemberRepository (new: insert, update, selectScoped,
                               deleteScoped, membersOf — extracted from MemberService),
                               MemberDeletion (the scoped-deletion outcome, declared beside
                               `deleteScoped` at the persistence level)
BASE/money/                    unchanged (shared horizontal: CurrencyResolution, MoneyMapping)
BASE/web/                      unchanged + GroupContextResolver (new, D-C) + its
                               WebMvcConfigurer registration (new)
```

- **`GroupLookup` and `GroupService.byToken` are deleted** (change from the previous draft, reconciling the repository mandate with D-4 — see D-C): the token-hash query moves into `GroupRepository.findByTokenHash`, and the public resolution surface becomes `GroupService.resolve(token): ResolvedGroup?` — `null` when the token matches no group, which keeps the indistinguishable unknown/malformed-token property in the type system (the caller cannot ignore it). The `MustUseReturnValues` guard `GroupLookup` provided is replaced by nullability.
- **`GroupRead` is deleted**: after D-4, `GroupService.read(resolved)` has no failure mode (the resolver already rejected unknown tokens); `read` returns `GroupResponse` directly. `GroupRename` keeps `Renamed | InvalidName`.
- **`MemberList` is deleted** for the same reason; `list` returns `MemberListResponse`. `AddMember` keeps `Added | InvalidName`; `UpdateMember` keeps `Updated | InvalidName | NotFound` (member-level only now); `DeleteMember` is unchanged (`Deleted | NotFound | Referenced`).
- `GroupAccess.check(GroupToken, MoneyDto)` keeps its signature: it stays the amount-carrying gate for future slices and is exercised through the test probe (`GroupAccessProbeController`, test tree only). Internally it switches from `byToken`/`GroupLookup` to `resolve` (`?: return GroupAccessResult.GroupNotFound`). When expense slices arrive they will call it with the resolved context; that adaptation belongs to those changes.
- `persistence/` top-level package is deleted; `PersistenceSmokeTest` (app-level Flyway/transaction-manager wiring, slice-agnostic) moves to `support/`. There is no shared persistence infrastructure to relocate — Exposed/DataSource wiring is Spring Boot autoconfiguration.
- Flyway migrations in `backend/src/main/resources/db/migration/` stay byte-identical; moving Exposed `Table` objects and query code between packages does not change DDL. **No migration is generated or added in this change.**
- **Build wiring consequence (sanctioned at the gate)**: the Exposed migration plugin is configured with `tablesPackage.set("com.example.harnessengineeringspring.persistence")` (`backend/build.gradle.kts`, `exposed { migrations { ... } }` block). When the top-level `persistence` package dies (task 3.1), this must be repointed to the slice persistence packages via the list form — `tablesPackages.set(listOf("com.example.harnessengineeringspring.group.persistence", "com.example.harnessengineeringspring.member.persistence"))` (supported by the plugin version in use) — otherwise `:backend:generateMigrations` breaks. `generateMigrations` stays an explicit-only task; nothing in `check` runs it.
- **Cross-slice FKs are legal (gate decision: "allowed for tables, not for DSL")**: `Members.groupId references Groups.id` becomes a `member.persistence` → `group.persistence` edge under the new layout and stays legal — schema definitions between table objects are the one sanctioned cross-slice sub-package access (design D-F rule 1, ADR decision 3). Cross-slice *query/DSL* access to another slice's tables is forbidden and, where a slice's own table object is involved, bytecode-undetectable — governed by the ADR heuristic instead.

## D-C Group-token resolution (user decision D-4)

**Mechanism: Spring WebMvc `HandlerMethodArgumentResolver`, not an interceptor.** The resolver applies only to handler methods that declare a `ResolvedGroup` parameter, so token-less routes (`POST /api/v1/groups`) need no path-pattern exclusion list — they simply do not declare it. An interceptor would run for every request and need manual route matching.

**Where the type lives: the group slice root, named `ResolvedGroup`.** It carries the verified `Group` (`data class ResolvedGroup(val group: Group)`). It cannot live in `web/`: both slice services accept it as their input (D-C signatures), and slice code outside `api/` may not depend on `web/` (D-F); a web-owned type would also make the group slice import web for its own identity. It is more than an alias for `Group`: a bare `Group` could come from anywhere; `ResolvedGroup` is produced only by the group slice, after token verification (`GroupService.resolve` constructs it), and reaches controllers only via the resolver — enforced by the controllers-never-touch-`GroupToken` rule.

**Resolver mechanics** (`BASE/web/GroupContextResolver.kt`, one file with its `@Configuration` registration class):
- `supportsParameter`: parameter type == `ResolvedGroup`.
- `resolveArgument`: read the `token` URI template variable from `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`; missing/blank → the same 404 path as an unknown token (indistinguishable `not_found` per group-access). Call `GroupService.resolve(GroupToken(raw))` → non-null → return it; `null` → `throw ApiException(ApiError.NotFound(message = "no such group"))`.
- Registered via `WebMvcConfigurer.addArgumentResolvers` in a new `@Configuration(proxyBeanMethods = false)` class in the same file.

**Signature follow-ups**: `GroupController.read(resolved: ResolvedGroup, atLeastSeq)` / `rename(resolved, request)` / unchanged `create`; `GroupService.resolve(token)` / `read(resolved)` / `rename(resolved, request)`; `MemberController` and every `MemberService` use case take `ResolvedGroup`. `MemberService` no longer depends on the group slice at all beyond the `ResolvedGroup` type (a root type — legal under D-2). `GroupAccess` switches to `resolve` internally.

**Accepted wire nuances** (approved at the gate, recorded as ADR Decision 4 consequences):
1. Unknown group token on `PATCH`/`DELETE` member routes: message `no such member` → `no such group`. Status 404 + `not_found` unchanged; no test pins message text.
2. Precedence: unknown/invalid token combined with an invalid body now 404s before body validation (previously 400 on rename/add/update where the service checked the body first). Unpinned by tests and specs; each spec scenario individually still holds.

The exact-preserving alternative — resolver returns a sealed `Verified|Unverified` context and every controller maps `Unverified` itself — was rejected at the gate: it re-adds per-route not_found mapping that D-4 exists to remove.

**Test-only probe exemption**: `GroupAccessProbeController` (test tree) keeps calling `GroupAccess.check(GroupToken(...))` with the token in the body. ArchUnit analyzes production code only (`DoNotIncludeTests`), so the controllers-never-touch-`GroupToken` rule is not violated by it.

## D-D Transaction-boundary policy

One `PlatformTransactionManager` exists (Exposed's `SpringTransactionManager`, see `PersistenceSmokeTest`). Policy, stated for all future slices:

- **Each root (or application) service method owns its transaction** (`@Transactional` on the service method). Repositories are transaction-less helpers running inside the caller's scope — a repository method must never open its own transaction.
- **The resolver's lookup is its own read-only transaction** (`resolve` is `@Transactional(readOnly = true)`); a write that follows runs in a second transaction, trusting the `ResolvedGroup` and scoping rows by `group.id`. This is accepted: a group cannot be deleted today, so no read-verify-write race exists; when group deletion ever arrives it must re-verify inside the write transaction (noted in the ADR consequences).
- **When one slice's service calls another slice's root service** (future: expenses → group), the inner `@Transactional` joins the caller's transaction (default REQUIRED propagation, single transaction manager) — that is the intended mechanism for multi-slice consistency. The D-2 rule constrains *which* types are reachable, not transaction topology.

## D-E Test-tree organization (mirrors the slice layout)

```
test/.../architecture/        ArchitectureTest, FieldInjectionRuleTest (extended/added rules)
test/.../group/api/            GroupControllerTest
test/.../group/domain/         GroupTokenGeneratorTest
test/.../group/persistence/    GroupsMigrationTest
test/.../member/api/           MemberControllerTest
test/.../member/persistence/   MembersMigrationTest
test/.../support/              IntegrationTest, TestcontainersConfig, PersistenceSmokeTest (moved)
test/.../web/                   existing web tests + GroupContextResolverTest (new)
test/.../probe/, money/        unchanged
```

Controller tests live in `api/` next to the controller, migration tests in the slice's `persistence/` next to its table, generator tests in `domain/`. `PersistenceSmokeTest` is app-level wiring, not slice property, so it sits in `support/` with the other shared test infrastructure. Probe controllers/tests stay in `probe/` — they are retired slice-by-slice as real endpoints arrive. No `application/` test packages exist (no `application/` packages are created in this change).

## D-F ArchUnit enforcement (decision D-3; executable in `ArchitectureTest`)

The frozen shared top-level allowlist is a constant in `ArchitectureTest`:

```kotlin
private const val BASE = "com.example.harnessengineeringspring"
private val SHARED_TOPS = ["web", "money"]   // frozen; extending it amends the ADR
```

A new top-level package is automatically treated as a slice by the rules below — it gets root-only cross access and must follow the anatomy; adding a *shared* top-level package means editing `SHARED_TOPS` (or the shared ignore below), which per the ADR requires a new/superseding ADR.

**Rule 1 — cross-slice access goes through the slice root (with the FK exception, user decision at the gate: "allowed for tables, not for DSL")** (generic; no slice is enumerated). Library formulation preferred over a hand-written condition:

```kotlin
@ArchTest
val cross_slice_access_goes_through_the_slice_root: ArchRule = slices()
    .matching("$BASE.(*)..")
    .should().notDependOnEachOther()
    .ignoreDependency(alwaysTrue(), resideInAPackage("$BASE.(*)"))        // target in ANY slice root: allowed
    .ignoreDependency(alwaysTrue(), resideInAPackage("$BASE.shared.."))    // the :shared wire contract: allowed for all
    .ignoreDependency(alwaysTrue(), resideInAnyPackage(*SHARED_TOPS.map { "$BASE.$it.." }.toTypedArray())) // shared zones
    .ignoreDependency(assignableTo(Table::class.java), assignableTo(Table::class.java)) // FK: table -> foreign table
    .because(
        "a slice's public API is its root package; api/, domain/, application/, persistence/ are internal, " +
        "except that a table object may reference another slice's table object for an FK definition " +
        "(vertical-slices ADR, decisions D-2/D-3)",
    )
```

(`org.jetbrains.exposed.v1.core.Table`; the assignability predicate's exact spelling — `JavaClass.Predicates.assignableTo` or a described `isAssignableTo` lambda — per the `archunit` skill. The `SHARED_TOPS`-derived clause — `$BASE.web..` / `$BASE.money..` — makes the frozen shared zones legal targets wholesale, root and sub-packages; it matches the persisted rule in `ArchitectureTest`.)

How it works: every top-level package (including `web`, `money`, `shared`) becomes a slice; cross-slice edges are legal in exactly four shapes: the *target* sits in a slice root package (`$BASE.(*)` = exactly one segment below `BASE` — which already covers today's flat `web` and `money` packages), the target is in the `:shared` wire contract, the target is in a shared-zone top from `SHARED_TOPS` (root or sub-package — load-bearing for any *future* `web`/`money` sub-packages), or the edge is **table object → table object** — the FK/schema-definition case (`Members.groupId references Groups.id` is a dependency from `member.persistence.Members` to `group.persistence.Groups` and is legal). Everything else — a repository, service or controller in `member` reaching `group.persistence`, `group.domain`, `group.api` or `group.application`, including calling query code on the foreign table — is a violation. Intra-slice dependencies (`group` root → `group.persistence` repository) are within one slice and untouched.

**Why this shape (enforceability analysis, folded from the gate decision).** The naive split — "foreign `Table` targets legal from anywhere" plus "ban method invocations owned by a foreign table class" — is not implementable: in Kotlin bytecode `references(Groups.id)` (the legal FK case) and `where { Groups.id eq x }` (the forbidden cross-slice query case) are *the same instruction*, a getter call on the `Groups` object — a bytecode rule cannot tell them apart, and banning table-owned method calls outright would break every FK definition. The chosen formulation is the strongest enforceable one: the FK privilege is scoped to **table objects as origins** (`ignoreDependency(assignableTo(Table), assignableTo(Table))`), so any *non-table* class — repository, service, controller — that so much as references a foreign table class is already a rule-1 violation; it never gets far enough to call DSL on it. **Residual, documented honestly**: bytecode rules cannot police what a slice's own table object does with its FK privilege (a `Table` object could host a query method that reads foreign-table columns — the getter calls are indistinguishable from FK definitions). That residual is governed by the ADR heuristic (flag the smell, the *user* decides whether a refactor is granted — never the agent) and by review, not by bytecode rules. Rule 2 below remains the primary DSL guard: no class outside `..persistence..` touches the DSL at all, cross-slice or not.

Implementation caveat: verify `matching("$BASE.(*)..")` also contains classes in the slice root package itself (`$BASE.group`) — `..` should match zero trailing segments, but the bite-proof (task) must include a violation *from* a root class (e.g. `GroupService` importing `member.persistence.Members`) as well as *to* a sub-package. If root classes turn out not to be included, fall back to `slices().assignedFrom(SliceAssignment)` that maps every class under `BASE` with at least one package segment to `SliceIdentifier.of(firstSegment)` (ignoring the bare `BASE` package); same ignore clauses.

**Rule 2 — the Exposed DSL and table objects stay inside `persistence/`:**

```kotlin
@ArchTest
val exposed_stays_inside_slice_persistence: ArchRule = noClasses()
    .that().resideOutsideOfPackage("..persistence..")
    .should().dependOnClassesThat().resideInAnyPackage("org.jetbrains.exposed..")
    .orShould().dependOnClassesThat(/* Exposed Table subtypes: assignableTo org.jetbrains.exposed.v1.core.Table */)
    .because(
        "repositories are the only query surface; services never touch the Exposed DSL or table " +
        "objects directly (vertical-slices ADR, decisions D-1/D-3)",
    )
```

Two targets are forbidden outside `..persistence..`: (a) any class in `org.jetbrains.exposed..` (the DSL and API types — covers query functions like `select`/`insert`), and (b) **subclasses of `org.jetbrains.exposed.v1.core.Table`** — the table objects live in slice `persistence/` packages, so a package pattern cannot catch a root service importing its own slice's `Groups`; an assignability predicate can (`areAssignableTo`/`assignableTo` on the `Table` class — consult the `archunit` skill for the exact predicate spelling; the table objects and any user subclass match, `Table` itself is already covered by (a)). This rule is red against today's code (`GroupService`, `MemberService` use the DSL and the table objects) and drives the repository extraction; it is green once queries live in `GroupRepository`/`MemberRepository`. `..persistence..` matches the sub-packages (`group.persistence`, `member.persistence`) generically, so repositories importing their own tables are exempt.

**Rule 3 — only a slice's `api/` layer may depend on `web`:**

```kotlin
@ArchTest
val only_slice_api_depends_on_web: ArchRule = noClasses()
    .that().resideOutsideOfPackage("..api..")
    .and().resideOutsideOfPackage("$BASE.web..") // web's own classes are exempt
    .should().dependOnClassesThat().resideInAPackage("$BASE.web..")
    .because("HTTP plumbing is an adapter concern; within a slice only api/ touches it (vertical-slices ADR, D-1)")
```

`..api..` also matches `shared.api` — harmless, the `:shared` module physically cannot depend on backend code. This rule forbids the slice root services, `application/`, `domain/` and `persistence/` from HTTP concerns (`ApiException`, resolver types).

**Rule 4 — controllers receive the resolved context, never raw tokens (D-4):**

```kotlin
@ArchTest
val controllers_never_touch_group_tokens: ArchRule = noClasses()
    .that().areAnnotatedWith(RestController::class.java)
    .should().dependOnClassesThat(/* equivalentTo GroupToken, exact class */)
    .because("the web resolver resolves the path token; controllers receive ResolvedGroup (vertical-slices ADR, D-4)")
```

Formulate the target as the exact `GroupToken` class (`JavaClass.Predicates.equivalentTo(GroupToken::class.java)` or the `dependOnClassesThat(Class)` overload — consult the `archunit` skill at implementation). Probe controllers are test code and invisible to the analysis.

**Rule 5 — `application/` is reachable only from the slice root** (intra-slice; may check zero classes until an `application/` package exists):

```kotlin
@ArchTest
val application_services_are_called_only_by_root_services: ArchRule = noClasses()
    .that().resideInAnyPackage("..api..", "..domain..", "..persistence..")
    .should().dependOnClassesThat().resideInAPackage("..application..")
    .allowEmptyShould(true) // no application/ package exists yet; task 6.4 proves it bites
    .because(
        "application services exist to keep root services focused; only the slice's root services " +
        "may call them (vertical-slices ADR, decision D-1)",
    )
```

Cross-slice callers of `application/` are already impossible via rule 1 (sub-package targets); this rule closes the intra-slice gap (`api/`, `domain/`, `persistence/` may not call `application/`). It deliberately checks zero classes today — `allowEmptyShould(true)` with the same comment convention as `no_field_injection` — and the verify-then-revert task creates a temporary `application/` package plus a violating `persistence/` class to prove it bites.

**Rule 6 — existing controller rule extended (D8 lineage).** `rest_controllers_do_not_depend_on_persistence` (added in `groups-and-members` design D8) gains `..domain..` (and with it the `application/` concern is covered by rule 5):

```kotlin
val rest_controllers_do_not_depend_on_persistence: ArchRule = noClasses()
    .that().areAnnotatedWith(RestController::class.java)
    .should().dependOnClassesThat().resideInAnyPackage("..persistence..", "..domain..", "org.jetbrains.exposed..")
    .because(
        "controllers are the HTTP boundary; use cases stay behind the slice root service " +
        "(groups-and-members D8, generalized by the vertical-slices ADR)",
    )
```

Controllers talk to their slice's root service only — not to another slice's or their own `domain/`/`persistence/`. The field name is kept for lineage (it is the same constraint, tightened).

**Unchanged rules:** `no_production_code_uses_jackson`, `persistence_uses_exposed_dsl_only` (the DAO ban now automatically covers the slice `persistence/` packages — `..persistence..`-style patterns already match at any depth), `no_field_injection`, `ledger_and_persistence_do_not_depend_on_shared_api` (its `..persistence..` and `..ledger..` patterns generalize to slice sub-packages and the future ledger slice unchanged — D7 lineage preserved).

**Every new rule is proven to bite** with the repo's verify-then-revert pattern: add the rule, create a temporary violating production class, run `:backend:test` to watch it fail, revert the violation, keep the rule green. No freezing: the target state violates nothing.

## Test boundaries (per behavior)

| Behavior | Boundary | Catches | Misses |
| --- | --- | --- | --- |
| Resolver resolution + rejection (D-4) | `web/GroupContextResolverTest`, shared `@IntegrationTest` context, **direct component boundary**: call the resolver with a `MockHttpServletRequest` carrying the `token` template variable, real `GroupService` + real Postgres — created group resolves to a `ResolvedGroup` with the right id; unknown/malformed/blank token yields null from `resolve` and the resolver throws `ApiException(NotFound)`; plus one **HTTP-boundary** scenario per controller family (unknown token on a member route and on a group route → 404 `not_found` envelope; `POST /api/v1/groups` still 201) to prove the WebMvc wiring | wrong resolution, wrong rejection type, missing registration | nothing behavioral — the envelope rendering itself is pinned by `ApiErrorEnvelopeTest` |
| Repository extraction (group + member) | existing `GroupControllerTest` / `MemberControllerTest` / probe tests (MockMvc, shared context, Postgres) — no assertion edits | any behavior drift while query code migrates from services to repositories, including the `GroupAccess` switch to `resolve` (pinned by `GroupAccessProbeTest`) | — |
| Whole refactored HTTP surface (regression net) | same existing tests, plus `GroupsMigrationTest`/`MembersMigrationTest` (JDBC schema pins, moved next to their tables) and `PersistenceSmokeTest` (moved to `support/`) | any accidental wire or DDL change during the moves and the D-4 rewiring | — |
| Slice-boundary architecture (D-2/D-3) | `ArchitectureTest` (ArchUnit, production bytecode, white-box by design) | cross-slice sub-package access, DSL/table objects outside `persistence/`, web outside `api/`, tokens in controllers, `application/` reached from anywhere but the slice root | structure only, zero behavior |

No new unit tests for the pure-move parts; the new ArchUnit rules and the resolver are the only new tests. Backend tests need Docker (Testcontainers `postgres:18-alpine`).

## Impact on queued changes

Flagged for their own planning (via `openspec-update-change` when each is picked up), not edited here:
- `ledger-core`: proposal line "backend persistence and runtime wiring; two migrations" — its design must put tables, trigger wiring and repositories in the ledger slice's `persistence/`, query only via repository; its ArchUnit rule references (`..ledger..`, `..persistence..`) already generalize. Future FK pairs (e.g. ledger events → groups/members, expenses → groups) are legal per the amended ADR decision 3 (table → foreign table) and need no exception entries in any rule; it is cross-slice DSL/query access to foreign tables that triggers the heuristic.
- `expenses-equal-split`, `expenses-exact-split`, `expenses-multiple-payers`, `expenses-shares-percent`, `balances`, `settlements`, `suggested-transfers`, `expense-restore`, `activity-feed`: each creates a new slice — copy the D-A anatomy (root services call repositories, never the DSL; `application/` only when a root service delegates real work); amount-carrying controllers receive `ResolvedGroup` (never tokens) and call `GroupAccess` from the group root.
- `web-shell`: frontend only, unaffected.

## Out of scope / follow-ups

- No Gradle module split per slice (D-3: enforcement stays ArchUnit); the ADR records how a later split would supersede it.
- No `application/` packages for `group`/`member` (optional layer; would be created by a change that needs it).
- No changes to `:shared`, `:web`, `money/`, `web/` logging/SPA/error-advise machinery beyond adding the resolver.
- No new Flyway migration; migrations stay byte-identical.
- No re-shaping of `GroupAccess` for future slices beyond its internal switch to `resolve`.

## Review loop addendum (implementation provenance)

The implementation went through two review-fix rounds after these artifacts were written. Facts, so the round-3 panel and the archive gate can reconcile the artifacts with the implemented state; no decision text changes.

- **Round 1** (implementer, fix mode): four characterization scenarios added pinning the two approved wire nuances by status and error *type* (message text deliberately not pinned — D-C's waiver holds): `GroupContextResolverTest`'s member-add scenario (body changed to invalid + renamed), `GroupControllerTest`'s rename-route combined unknown-token + invalid-body case, and `MemberControllerTest`'s PATCH and DELETE unknown-group-token scenarios. These are new scenarios, not edits to pre-existing assertions — the "existing tests stay green without assertion edits" regression-net claim is unaffected. Task 1.1 scope addendum: materializing the first ADR also updates CLAUDE.md's module-map row for `docs/adr/` (the old "not yet created" wording became false). Reading of approved nuance (a), applied here rather than a new deviation: it mechanically covers combined cases — unknown token + malformed `memberId` on member PATCH/DELETE answers "no such group", because the resolver runs before the handler parses the id; status 404 + `not_found` unchanged, message text unpinned per the D-C waiver.
- **Round 2** (implementer, fix mode): HELP.md's `generateMigrations` sentence repointed to the slice persistence packages (matching the `tablesPackages` repoint, D-B); the literal message pin was removed again from `GroupContextResolverTest` (three-way indistinguishability retained, no message text pinned); `@file:MustUseReturnValues` added to `GroupService.kt`, `GroupRepository.kt` and `MemberRepository.kt`, plus `@MustUseReturnValues` on `MemberDeletion` — the return-value checker marks *declaration* sites (verified empirically with compiler probes), so the guard sits on the result-bearing declarations, and annotating `ResolvedGroup` was declined as mechanically wrong (D-B's note that nullability replaces the old `GroupLookup` guard refers to `resolve`'s nullable return; these annotations cover the remaining sealed results); `SHARED_TOPS` is wired into rule 1's shared-target ignore clauses — load-bearing for any *future* `web`/`money` sub-packages: today's flat packages are already legal targets via the any-slice-root ignore, the `SHARED_TOPS`-derived clause is what would make their sub-packages legal too, and the constant is the ADR's extension point (adding a shared top-level means editing it, i.e. amending the ADR) — an enforcement detail consistent with ADR decision 3, not an ADR change.
- **Types forced by the rules**: `MemberRow` (`member/domain/`, a row POKO) and `MemberDeletion` (declared in `member/persistence/` beside `deleteScoped`) exist because persistence may not depend on `shared.api` (`ledger_and_persistence_do_not_depend_on_shared_api` — the deletion outcome cannot be a wire DTO) and because services may not touch Exposed rows (the repository mandate / `exposed_stays_inside_slice_persistence` — the repository returns POKOs, not rows).
- **Build fact**: `:backend:generateMigrations` was exercised after the `tablesPackages` repoint — 0 migrations generated, `db/migration` byte-identical.
