#!/usr/bin/env python3
"""Stop / SubagentStop / TaskCompleted: run `check` (tests incl. ArchUnit + detekt) for Gradle projects whose build inputs changed.

This is the gate that also catches edits the per-edit detekt hook never sees (Bash/sed/heredoc writes).

A project counts as changed when the content of its src/ + build file (or of a global build file such as
settings.gradle.kts, gradle/libs.versions.toml, buildSrc/) differs from the last green run. Nothing changed
-> no Gradle invocation at all. A fresh failure blocks (exit 2) so the agent keeps working; the same
failing state is not re-run or re-blocked, which prevents stop-hook loops when the agent can't fix it.
"""

import fcntl
import json
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gradle_modules  # noqa: E402

MAX_REPORT_LINES = 150
MAX_LINES_PER_FAILURE = 30


def load(path: pathlib.Path) -> dict:
    try:
        return json.loads(path.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {"green": {}, "failed": {}}


def failure_summary(output: str) -> str:
    """Failed tests with their first stack lines, compiler errors, detekt findings and Gradle's 'What went wrong'."""
    lines = output.splitlines()
    picked: list[str] = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if line.rstrip().endswith("FAILED") and not line.startswith("> Task"):
            picked.append(line)
            # assertion message + power-assert diagram + first stack frames, until the next unindented line
            for detail in lines[i + 1:i + 1 + MAX_LINES_PER_FAILURE]:
                if not detail.startswith((" ", "\t")) and detail.strip():
                    break
                picked.append(detail)
        elif line.startswith("e: "):
            picked.append(line)
        elif line.startswith("* What went wrong:"):
            end = next((j for j in range(i, len(lines)) if lines[j].startswith("* Try:")), len(lines))
            picked.extend(lines[i:end])
            i = end
            continue
        i += 1
    return "\n".join(picked[:MAX_REPORT_LINES]) or "\n".join(lines[-40:])


def main() -> int:
    event = json.load(sys.stdin)
    root = gradle_modules.git_toplevel(pathlib.Path(event.get("cwd") or "."))
    if root is None or not (root / "gradlew").is_file():
        return 0

    with open(gradle_modules.state_file(root, "check.lock"), "w") as lock:
        # Parallel subagents finishing together: one runs Gradle, the others then see the fresh state.
        fcntl.flock(lock, fcntl.LOCK_EX)
        state_path = gradle_modules.state_file(root, "check.json")
        state = load(state_path)
        current = gradle_modules.fingerprints(root)
        changed = sorted(m for m, digest in current.items() if state["green"].get(m) != digest)
        if not changed:
            return 0
        to_run = [m for m in changed if state["failed"].get(m) != current[m]]
        if not to_run:
            still_failing = ", ".join(gradle_modules.gradle_path(m) for m in changed)
            print(json.dumps({"systemMessage": f"check still failing (unchanged since last run): {still_failing}"}))
            return 0

        tasks = [gradle_modules.task_path(m, "check") for m in to_run]
        run = subprocess.run(
            [str(root / "gradlew"), "--console=plain", "--continue", *tasks],
            cwd=root, capture_output=True, text=True,
        )
        if run.returncode == 0:
            for module in to_run:
                state["green"][module] = current[module]
                state["failed"].pop(module, None)
            state_path.write_text(json.dumps(state, indent=2))
            print(json.dumps({"systemMessage": "check passed: " + " ".join(tasks)}))
            return 0

        for module in to_run:
            state["failed"][module] = current[module]
        state_path.write_text(json.dumps(state, indent=2))

    failed_tasks = sorted(set(re.findall(r"^> Task (\S+) FAILED", run.stdout + run.stderr, re.M)))
    print(
        f"check failed for changed Gradle project(s) - ran {' '.join(tasks)}; failed tasks: {', '.join(failed_tasks) or 'see below'}.\n"
        "The work is not done until these pass. Fix the code (not the tests, unless the test itself is wrong; "
        "never detekt.yml or the baseline), "
        "or report the failure explicitly if it can't be fixed. Details: Gradle MCP `query_build`, build/reports/tests/ "
        "or build/reports/detekt/.\n\n"
        + failure_summary(run.stdout + run.stderr),
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
