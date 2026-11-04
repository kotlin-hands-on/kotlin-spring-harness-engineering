---
name: improve-architecture
description: Surface opportunities to improve codebase architecture - dispatches fresh-context subagents to scan for shallow modules, poor locality, leaky seams and hard-to-test areas, verifies every candidate against the code, and writes a ranked report with recommendation badges (Strong / Worth exploring / Speculative). Use when the user asks for architecture improvements, refactoring opportunities or an architecture assessment, or as the scan step of /refactor.
---

# Improve architecture - find the opportunities first

Find refactoring opportunities that make the codebase **deeper** - each module hiding more machinery behind a smaller interface - and rank them by leverage. This skill is read-only analysis plus one report file. It never writes code or OpenSpec artifacts: picked candidates continue via `/refactor` (explore -> proposal -> implementation), never from here.

## Vocabulary - the lens every candidate is seen through

- **Module** - anything with a name and a boundary: a Gradle module (`shared`, `backend`, `web`), a package, a class, a function. Its **interface** is everything a caller must understand: signature, types, error semantics, configuration, ordering rules.
- **Deep vs shallow** (Ousterhout, *A Philosophy of Software Design*) - a module is *deep* when it hides a lot of complexity behind a small interface. It is *shallow* when the interface is as wide as what it hides: pass-through layers, one-method wrappers, types that only rename another type, config a caller must understand to use one method. Shallow modules add cost while hiding nothing.
- **Deletion test** - if you deleted the module and inlined its body at the call sites, would the code be easier to read and no harder to test? Then it is too shallow to exist.
- **Locality** - a change to one behaviour should touch one place. The smell: shotgun surgery, one feature smeared across controller -> service -> repository with each hop adding only a rename.
- **Seam** - a place where behaviour can be substituted for testing or variation: a port, an injected function, a boundary interface. A seam is *leaky* when callers or tests must know what is behind it - mocking own code instead of passing a fake through a real boundary, or reaching into internals to set up a case.
- **Leverage** - value divided by code touched. A candidate that stops a recurring pain beats one-file beautification.

**Settled decisions are constraints, not suggestions**: the capability specs in `openspec/specs/**`, the `design.md`s in `openspec/changes/archive/**`, and existing ArchUnit rules (search `src/test` for `@AnalyzeClasses`). A candidate that bends or reverses one must say so in its card - never silently. The specs also hold the domain language; use their terms.

## Step 1 - Scope

- A **scope hint** (module, package, area, "hot spots only") may come from the caller - respect it; otherwise scan the whole repo. State what was scanned.
- Hot spots first: `git log --since="6 weeks ago" --name-only --pretty=format: --no-merges | grep -v '^$' | sort | uniq -c | sort -rn | head -30`. Churn is where a deepening pays off most.
- Read around the hot spots: the matching capability specs (`openspec list --specs`, then `openspec show "<id>" --type spec`), ArchUnit rules, and the active change names in `openspec/changes/` - work in flight is off limits: don't propose moving something an active change is rebuilding. Any code discovery here starts with `jbcontext search`.
- Write down: hot-spot files, matching capabilities and their terms, settled decisions, active changes.

## Step 2 - Dispatch fresh eyes

You have read this code before and gone blind to its friction; fresh-context subagents have not. Dispatch `general-purpose` subagents with an explicit **read-only instruction** (analysis only - no edits, no commits, no formatting runs): one for a small scope, one per Gradle module or layer, capped at four, when broad. Spawn them all in one message, in the background.

Each explorer prompt is self-contained and carries: the scope + hot-spot list, the vocabulary lenses above (copied), the settled decisions, the active change names, and:

- First code-search action: `jbcontext search` - one focused query per friction kind (e.g. "classes that wrap a repository without adding behaviour"), then read the cited code in full.
- Hunt for: shallow modules (run the deletion test), shotgun surgery, leaky seams (tests mocking own code, tests reaching into internals), duplication that signals a missing deep module, boundaries an ArchUnit rule could guard.
- Return **candidates with evidence only**: `file:line` for every claim, the lens it is seen through, and a solution *direction* - no API design.

## Step 3 - Verify and rank

- Read every cited line yourself before it enters the report. Drop what you can refute at the cited line; demote what the specs already answer (the behaviour is intended); merge duplicates.
- Rank by leverage: recurring pain x (testability + locality + navigability) / effort. Testability = fewer mocks, more black-box boundaries. Navigability = humans and agents locate and reason about the code faster.
- 3-8 candidates is the sweet spot; pad with speculative ones only if genuinely interesting. Badge each:
  - **Strong** - verified evidence, high leverage, consistent with settled decisions.
  - **Worth exploring** - real friction, but the direction needs a design decision first.
  - **Speculative** - plausible; needs a spike or more evidence.

## Step 4 - Write the report

Markdown, outside the repo - it is an input to the next step, not an artifact to keep:

`${TMPDIR:-/tmp}/improve-architecture-<yyyymmdd-hhmm>.md`

````markdown
# Architecture opportunities - <scope> (<date>)
Hot spots: <top churn files> | Scanned by: <explorers and what each covered>

## Candidates (ranked)
### 1. <imperative title> - Strong
**Files**: <the 2-6 files, with line refs>
**Problem**: <2-4 sentences: which lens, with evidence at file:line>
**Direction**: <1-3 sentences where the depth/seam goes - no interface design, that is the proposal's job>
**Wins**: <testability / locality / navigability bullets>
**Effort**: S | M | L   **Risks**: <migration, ordering, blast radius>
**Settled decisions touched**: <spec / archived design / ArchUnit rule - extends or reverses?>

## Rejected (don't re-raise without new evidence)
- <candidate> - <load-bearing reason> (if it is a dependency-direction call, name the ArchUnit rule that would settle it)

## Top recommendation
<one paragraph: the single candidate to start with and why>
````

Present in chat: the ranked list (number, title, badge, one-line problem, effort), the report path, and the top recommendation. ASCII diagrams are fine where they clarify a card.

## Handoff

- **Under `/refactor`**: stop here - the command runs its selection gate on the report.
- **Standalone**: ask which candidates to pursue; picked ones continue via `/refactor` (or `/opsx:explore` first). Never implement from this skill.
- A candidate rejected for a load-bearing reason stays in the report's Rejected section so future scans don't re-raise it; when the reason is a dependency direction, suggest the ArchUnit rule that would settle it.

## Never

- Sell restyling, renames or formatting as "architecture" - only depth, locality, seams and boundaries.
- Report a candidate you have not verified at the cited line yourself.
- Design interfaces or APIs in the report - direction only; `openspec-propose` designs against requirements.
- Reverse a settled decision silently, or propose moving code an active change owns.
- Edit anything outside the report file.

Source: inspired by Matt Pocock's `improve-codebase-architecture` skill; vocabulary from Ousterhout, *A Philosophy of Software Design*.
