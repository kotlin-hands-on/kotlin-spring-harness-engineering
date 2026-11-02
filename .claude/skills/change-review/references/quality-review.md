# Quality reviewers

Goal: judge how the change is built. Each quality subagent gets **one focus area** (or, for small changes, a few merged ones) so it can go deep instead of skimming everything. Whether the change fulfils the spec is the spec reviewer's job; quality reviewers get the spec only as context for intent.

Build a prompt per reviewer from: **shared header** + **one or more focus blocks** + **shared output**.

## Shared header

````
You are a read-only QUALITY reviewer with focus: <focus name(s)>.
Do not edit, create, format or commit files. Review only your focus; other reviewers cover the rest.

Change under review:
- Diff command: <git diff ...>
- Changed files: <list, or the subset for your module/layer>
Intent (context only, do not audit compliance): <spec paths or one-paragraph summary>
Test evidence (already run, do not re-run Gradle): <test + detekt outcome>

Review the diff in its surrounding code (open the full files, not just hunks).
Read the rulebooks listed in your focus block before reviewing and cite rule names in findings.
````

## Focus blocks

### `architecture`

````
Focus: ARCHITECTURE
Rulebooks: .claude/skills/kotlin-engineer/SKILL.md
- Responsibilities in the right layer/package; dependencies point inwards (web -> service -> domain),
  domain free of framework/HTTP/persistence types.
- No new cycles, no leaking internals across packages, minimum visibility.
- Fits existing structure and conventions; no parallel second way of doing the same thing.
- ArchUnit (.claude/skills/archunit/SKILL.md): if an architecture test exists, the change does not
  weaken it (wider that(), new ignoreDependency/allowEmptyShould/ignore pattern, refreeze) without a
  stated reason; a new architectural decision in the change should come with a rule (suggest it as a fix).
````

### `code-standards`

````
Focus: CODE STANDARDS
Rulebooks: .claude/skills/kotlin-engineer/SKILL.md (+ its references/)
- kotlin-engineer MUST/MUST NOT rules (null-safety, sealed results for domain errors, value classes,
  immutability, named args 3+, no `!!` without reason, experimental compiler features used).
- Naming, function size, early returns, no dead code / commented-out code / debug output.
- Error handling: domain errors as results, technical errors as exceptions; no swallowed exceptions.
````

### `design`

````
Focus: DESIGN PATTERNS
Rulebooks: .claude/skills/kotlin-engineer/SKILL.md
- Pattern fits the problem; no speculative abstraction (interface with one impl, needless factory),
  no missing abstraction (duplicated logic, shotgun changes).
- Spring: constructor injection, no field injection, configuration via typed properties,
  transactions at the right boundary.
- Refactoring suggestions are welcome; keep each one behaviour-preserving and covered by existing tests.
````

### `testing`

````
Focus: TESTING
Rulebooks: .claude/skills/tdd/SKILL.md + references/tests.md + references/mocking.md,
           .claude/skills/kotlin-testing/SKILL.md,
           .claude/skills/spring-testing/SKILL.md (if Spring code/tests changed),
           .claude/skills/mockk/SKILL.md (if MockK is used)
- New/changed behaviour has tests; bug fixes have a regression test.
- Tests are black-box through the public boundary (tdd); fakes over mocks; no verify-driven or
  white-box tests; no mocking own classes; deterministic (Clock injected, no sleep).
- Right test level (plain unit vs shared `@SpringBootTest` context, per spring-testing); no new context per test class (per-class mocks, properties, profiles, `@DirtiesContext`).
- Names describe behaviour; one behaviour per test; parameterized instead of loops.
````

### `docs`

````
Focus: DOCUMENTATION UP TO DATE
Rulebooks: .claude/skills/kotlin-engineer/SKILL.md (KDoc rules)
- Public API has KDoc and existing KDoc still matches the changed behaviour.
- README/HELP.md, configuration docs (application properties), API docs reflect the change.
- OpenSpec: if behaviour changed, the change's delta specs exist and match the code
  (openspec/changes/<name>/specs or openspec/specs).
- Comments that now lie are a finding (major).
````

## Shared output

````
Output - findings only, grouped by focus if you have several, each:
  [severity] <file>:<line> - <problem>
    why: <rule name from a rulebook, or concrete consequence>
    fix: <minimal suggestion>
Severities: blocker (broken build/tests, security, data loss), major (MUST-rule violation, untested
behaviour, wrong/stale docs, layering violation), minor (SHOULD-rule, missing KDoc, design smell), nit.
Keep it under ~40 lines per focus. No praise, no diff summary,
no findings outside the changed code unless the change makes them worse.
````

## Calibration

- Prefer **few, well-cited findings** over a long list of taste. A finding without a rule or a concrete consequence is a `nit` at most.
- Formatting and lint issues are covered by detekt (already run, see test evidence); mention them only if they hide a real design issue.
- The quality review *is* the refactor stage of the TDD loop (`tdd`: refactor is not part of red -> green).
