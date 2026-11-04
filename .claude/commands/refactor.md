---
description: "Refactoring run end to end: scan → report gate → explore → proposals → implement → review (fix ≤3) → archive → commit, with approval gates"
argument-hint: "[scope hint | path to an improve-architecture report]"
---

Run `$ARGUMENTS` (or the whole repo) through a full refactoring loop. You are the **orchestrator**: you run the `improve-architecture` skill (scan), hold the explore conversation, dispatch the `planner` (which runs `openspec-propose`), the `implementer` (which runs `openspec-apply-change`), the `change-review` skill and the `openspec-archive-change` skill, and relay their questions to the user (subagents can't ask). The skills own their workflows - this command only sequences them, adds the gates and the fix loop. You write no production code, tests or plan artifacts yourself; subagents can't spawn agents, so every dispatch happens from here.

Keep a running status block in each message to the user so they always know where the run is:

```
/refactor <scope> - step <n>/7 <name> | candidate <i>/<N> <change-name> | review round <r>/3 | gates passed: report … propose … archive … commit …
```

## 0. Preconditions

- `$ARGUMENTS` names an existing file → it's a report the `improve-architecture` skill wrote earlier: announce `Using report: <path>` and go straight to Gate 1. Otherwise it is a **scope hint** (module, package, area); empty means whole repo.
- `git status --short` not clean → show it and ask whether to continue on top of it (the unrelated changes would end up in every review scope and commit) or stop. Never stash or discard anything.
- `openspec list --json`: `"root": null` → this project isn't set up for OpenSpec; stop and ask (don't `openspec init` on your own).

## 1. Scan

Run the `improve-architecture` skill, passing the scope hint as its argument. It owns scoping, the fresh-context explorers, verification and writing the report to `${TMPDIR:-/tmp}/improve-architecture-*.md`; take the report path and ranked summary from its output. Zero candidates → say so, suggest a wider scope or a different churn window, stop.

## 2. ⛔ Gate 1 - report & selection (always stop here)

Show: the ranked candidate list (number, title, badge, one-line problem, effort), the report path, and the top recommendation. Ask which candidates to pursue.

- Answers may be numbers, "top one", "all Strong", constraints ("S effort only", "not web"), or hybrids - sequence a hybrid as steps. Record the picks and constraints.
- Rejecting every candidate → stop. Candidates turned down for load-bearing reasons are already in the report's Rejected section; future runs won't re-raise them.

## 3. Explore the report (main thread, `openspec-explore` stance)

Thinking only - explore never writes, and never captures changes here: proposals are the planner's job next.

- Re-verify each pick at its cited lines; drop stale ones from the pick list and say so.
- Surface what's *between* the picks: dependencies (B assumes the seam A introduces → fix an order), overlaps (merge or keep one), friction with settled decisions.
- Ask only the questions whose answers change the proposal (which boundary, where the seam goes, migrate or big-bang). Everything else becomes an **open question** for the planner - it cannot ask the user, exactly like `/ship`.
- Keep per-candidate **explore notes**: scope, direction, constraints, dependencies, open questions.
- User says "proceed" or nothing is pending → skip straight to proposals; the open questions surface at Gate 2.

## 4. Propose

Dispatch the `planner` per candidate with a self-contained prompt: the candidate number and title, its report section (paste it; also give the report path), the explore notes, the user's constraints from this conversation, and the list of active change names (the planner must not collide with one). `openspec validate <change> --strict` must pass for each.

Independent picks → dispatch the planners in parallel, in the background. Dependent picks (step 3 fixed an order) → plan the first one now; plan each dependent one only after the change it depends on is implemented.

### ⛔ Gate 2 - proposal approval (always stop here)

Per change: change name and path, the planner's summary, test boundaries, task count, and the **open questions**. Ask the user to approve, answer open questions, or request changes. Wait for the reply.

- Answers / change requests → fold them in with the `openspec-update-change` skill (or re-dispatch the planner with the answers), validate again, show the artifact diff, ask again.
- Never continue with unanswered open questions that affect behaviour.

## 5. Implement

Candidates one at a time, in the order explore fixed (dependencies before dependents).

1. Create and switch to `refactor/<change-name>` (local only, never push), cut from the current branch: the first candidate branches from where `/refactor` started, each later candidate from the previous candidate's branch - stacked, so each review sees only its own change.
2. Pre-check: `openspec instructions apply --change <name> --json`.
   - `state: "blocked"` → show the message (and `missingArtifacts`), stop and ask; don't dispatch.
   - `state: "all_done"` → nothing to implement; go to step 6 (review).
3. Dispatch the `implementer` in **implement mode**, in the background: "Apply OpenSpec change `<name>` following `openspec-apply-change`."
4. Report starts with `PAUSED:` → relay the issue and options and wait. Design issue / scope beyond the spec → fix the plan with `openspec-update-change`, pass Gate 2 again, then re-dispatch for the remaining tasks (the apply workflow resumes from the unticked tasks).
5. Verify, don't trust the report: `openspec list --json` must show `completedTasks == totalTasks` for the change, and the SubagentStop hook must have reported `check passed` (or nothing changed). Otherwise stop and ask. Don't review a half-built change.

## 6. Review + fix loop (max 3 rounds)

Run the `change-review` skill. Scope: `git diff <base>...HEAD` plus untracked files from `git status --short`, where `<base>` is the branch this candidate was cut from (`main` for the first candidate, the previous candidate's branch for the rest). Spec: the OpenSpec change. Reviewers are never shown earlier rounds' findings.

Verdict `APPROVE` → step 7 for this candidate.

While the verdict is `REQUEST_CHANGES` and fewer than 3 fix rounds have run:

1. Dispatch the `implementer` in **fix mode** with the change name and the `blocker` + `major` findings verbatim (`[severity] file:line - problem / why / fix`). `minor`/`nit` findings are not sent; collect them as follow-ups.
2. From its report, collect `declined` and `needs-spec`. A `needs-spec` finding → stop the loop and ask (it changes the plan → `openspec-update-change` → Gate 2 again).
3. Re-check `openspec list --json` (all tasks ticked again), then re-run the review on the same scope.

A finding the implementer `declined` that the next round raises again → don't loop on it; note both sides for the final report. After round 3 with blockers/majors left → **stop this candidate**: show the remaining findings and the declined ones, offer options with a pre-composed hybrid first (e.g. "micro-fix + focused 2-reviewer re-check instead of a full panel"), and ask whether to continue with the next candidate or abort the run. Never archive or commit a failed candidate on your own.

## 7. Finish & archive (per candidate)

### ⛔ Gate 3 - archive

Follow the `openspec-archive-change` skill for the change, as written - it owns the incomplete-artifact/task warnings, the delta-spec sync assessment, the inline `openspec-sync-specs` merge and the move to `openspec/changes/archive/YYYY-MM-DD-<name>/`. Its sync prompt **is** Gate 3. Prefix the skill's combined sync summary with: final review verdict, review rounds used, the requirement → status table from the last spec-compliance review, and the follow-ups (minor/nit + implementer follow-ups). Offer exactly the options the skill allows; "Cancel" stops `/refactor` for this candidate before archive and commit.

### ⛔ Gate 4 - commit

Show `git status --short`, `git diff --stat` and a proposed commit message. The commit contains code + tests, the archived change directory (with the removal of `openspec/changes/<name>/`) and the updated main specs under `openspec/specs/`:

```
<imperative summary of the refactor, ≤ 72 chars>

<what deepened/moved and why - 2-5 lines, from the report card>

OpenSpec: <change-name> (archived as <date>-<change-name>)
Review: APPROVE after <r> fix round(s)
Source: candidate <n> of improve-architecture-<yyyymmdd-hhmm>.md
```

Ask for approval or edits. On yes → stage the change's files explicitly (no `git add -A` if the precondition check showed unrelated changes) and commit on the candidate's branch. Never push, never merge into `main` - say how to do it instead.

Then: next candidate (go back to step 5) or, after the last one, the final report.

## Final report

Per candidate: change name, branch, commit hash (or the gate where it stopped and why), tasks done, tests added, review rounds and final verdict, declined findings, follow-ups. Plus: the report path and the candidates not picked - the backlog for the next run.

## Never

- Skip a gate, or treat silence or an earlier approval as approval for a later gate.
- Edit code, tests, specs, `detekt.yml`, baselines or hooks yourself to get past a step - dispatch the right agent or stop and ask.
- Implement outside `openspec-apply-change`, tick task checkboxes yourself, or archive outside `openspec-archive-change`.
- Answer a question an OpenSpec skill would ask the user on the user's behalf.
- Run reviewers yourself or show them earlier findings - independence is the point of the review.
- Exceed 3 fix rounds per change, or work on two candidates in the same working tree.
- Push or merge anything.
