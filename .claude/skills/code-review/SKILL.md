---
name: code-review
description: Review a change (working tree, branch, commit range or PR) by dispatching parallel read-only subagents - spec-compliance reviewer(s) check the implementation against its spec (OpenSpec change, issue, task), focused quality reviewers check architecture, code standards, design patterns, testing and whether docs are up to date. Use when the user asks to review code, a diff, a PR or an implemented change, before archiving an OpenSpec change, or for the refactor stage after a TDD red -> green loop.
---

# Code review - orchestrated by subagents

You are the **orchestrator**. You do not review the code yourself: you collect the inputs, dispatch independent reviewers in parallel, then merge, de-duplicate and rank their findings into one verdict.

Why several reviewers: "does it do what was asked?" and "is it built well?" are different questions, and "built well" itself has several angles. A single reviewer drifts into style nits and misses a missing requirement - or stops at the first layering problem and never looks at the tests. Separate, narrowly focused contexts keep each one thorough.

## Reviewer roster

There are two **kinds** of reviewer. How many subagents you start per kind depends on the size of the change (see below).

| Kind | Question | Prompt template |
|---|---|---|
| **Spec compliance** | Does the implementation do exactly what the spec asks - nothing missing, nothing extra, nothing contradicting? | `references/spec-compliance.md` |
| **Quality** | Is it built well? One subagent per focus area. | `references/quality-review.md` |

Quality focus areas (each defined in `references/quality-review.md`):

| Focus | Covers |
|---|---|
| `architecture` | Layering, dependency direction, package boundaries, visibility, fit with existing structure |
| `code-standards` | `kotlin-engineer` rules, naming, error handling, dead code |
| `design` | Pattern fit, speculative or missing abstraction, Spring wiring and transaction boundaries |
| `testing` | Coverage of changed behaviour, black-box tests, fakes over mocks, right test level |
| `docs` | KDoc, README/HELP.md, configuration docs, OpenSpec specs, stale comments |

### Sizing the dispatch

- **Default:** 1 spec-compliance reviewer + 1 quality reviewer **per focus area** (5) = 6 subagents.
- **Small change** (a few files, one concern): you may merge focus areas, e.g. `architecture + design` and `code-standards + docs` alongside `testing`. Never fewer than one spec and one quality reviewer.
- **Large change:** split further instead of overloading a reviewer:
  - Spec compliance: one reviewer per OpenSpec capability (`specs/<capability>/spec.md`) or per group of requirements.
  - Quality: one reviewer per focus area **and** per module/layer if the diff spans many (e.g. `testing` for `web` and `testing` for `persistence`).
- Skip a focus only if it cannot apply (e.g. `docs` for a test-only change) and say so in the report.

## Workflow

### 1. Fix the scope

Determine the exact change under review and write it down as a reproducible command:

- Uncommitted work: `git diff HEAD` (+ `git status --short` for untracked files).
- Branch / PR: `git diff <base>...HEAD` (base = merge-base with `main` unless the user names one).
- Commit range: `git diff <from>..<to>`.

Record the list of changed files (`git diff --name-only ...`). If the scope is ambiguous, ask the user - never review "the whole repo" by default.

### 2. Find the spec

The spec is what the change was supposed to implement. Look in this order and use **all** that apply:

1. **OpenSpec change** - `openspec list --json`; for the matching change read `openspec/changes/<name>/proposal.md`, `design.md`, `tasks.md` and the delta specs under `openspec/changes/<name>/specs/**/spec.md` (requirements + scenarios). Already archived? `openspec/changes/archive/` and the merged `openspec/specs/**/spec.md`.
2. **Issue / ticket / PR description** the user referenced.
3. **The user's task statement** in this conversation.

If no spec can be found, tell the user and run quality reviewers only - do not invent requirements for a spec reviewer.

### 3. Collect test evidence (once, before dispatch)

Run tests and static analysis yourself so the reviewers don't race on Gradle and all see the same results. Use the Gradle MCP (`using-gradle`): `gradle` with `test detekt`, then `query_build` for failures (`kind=TESTS` for failing tests, detekt findings from the task output / `build/reports/detekt/`).

Keep the outcome (pass/fail, failing tests, detekt findings). A red build or a detekt finding in changed code is a finding by itself - include it, don't fix it.

### 4. Dispatch all reviewers in parallel

Plan the roster (step "Sizing the dispatch"), then spawn **all subagents in a single message** (Claude Code: one `Agent`/Task tool call per reviewer in one response, general-purpose type) so they run concurrently. Fill the template from the reference file for each; every prompt must be self-contained, because subagents see nothing of this conversation:

- the scope command and changed-file list from step 1 (narrowed to the reviewer's module/files if you split by area),
- the spec file paths / text from step 2 (spec reviewers audit it; quality reviewers get it as context only),
- for quality reviewers: the focus block(s) assigned to them, copied from `references/quality-review.md`,
- the test evidence from step 3,
- the instruction that the review is **read-only** - no edits, no commits, no formatting runs.

Reviewers must report findings in the shared format below. Don't let a reviewer spawn further reviewers.

### 5. Merge and report

- **De-duplicate**: the same location flagged by several reviewers → keep one entry, note all angles.
- **Re-rank** by severity across all reports; drop findings you can refute by reading the cited line (check before dropping - don't drop on a hunch).
- **Coverage check**: every planned reviewer reported back; if one failed or ran out of budget, re-dispatch it rather than silently skipping its focus.
- **Verdict**: `REQUEST_CHANGES` if any `blocker` or `major` exists, otherwise `APPROVE` (list `minor`/`nit` as optional).
- Do **not** apply fixes unless the user asks. When they do, fix test-first (see `tdd`) and re-run the review on the new diff.

## Finding format (all reviewers)

```
[severity] <file>:<line> - <one-line problem>
  why: <rule / requirement / scenario violated, with reference>
  fix: <minimal concrete suggestion>
```

Severities:

| Severity | Meaning |
|---|---|
| `blocker` | Spec requirement missing or contradicted, broken build/test, data loss, security issue |
| `major` | Wrong behaviour in an edge case, MUST-rule violation (`kotlin-engineer`, `tdd`), untested behaviour, docs now wrong |
| `minor` | SHOULD-rule violation, unclear design, missing KDoc on public API |
| `nit` | Naming, formatting, taste |

## Final report shape

```
## Code review: <scope>
Spec: <source(s) used, or "none found - quality review only">
Tests: <outcome>
Reviewers: <list of dispatched reviewers and focus areas>
Verdict: APPROVE | REQUEST_CHANGES

### Spec compliance
<requirement/scenario → status table, then findings>

### Quality
<findings grouped by focus: architecture, code standards, design, testing, docs>

### Suggested next steps
<ordered list, blockers first>
```

## MUST NOT DO

- **Don't review yourself instead of dispatching** - your job is scope, spec, evidence, roster, merge.
- **Don't run the reviewers sequentially** or give one another's output - independence is the point.
- **Don't give one quality reviewer everything on a large change** - split by focus and, if needed, by module.
- **Don't pass vague scopes** ("recent changes", "related code") - always the exact diff command and file list.
- **Don't let reviewers edit code** or "quickly fix" things during review.
- **Don't report un-cited findings** - every finding has a `file:line` and a reason.

## Related skills

Reviewers apply these as their rulebook: `kotlin-engineer` (`../kotlin-engineer/SKILL.md`), `tdd` (`../tdd/SKILL.md`), `kotlin-testing` (`../kotlin-testing/SKILL.md`), `spring-testing` (`../spring-testing/SKILL.md`), `mockk` (`../mockk/SKILL.md`), `archunit` (`../archunit/SKILL.md`). OpenSpec workflow: `openspec-apply-change`, `openspec-archive-change` - run this review before archiving.
