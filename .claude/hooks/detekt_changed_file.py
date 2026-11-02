#!/usr/bin/env python3
"""PostToolUse(Edit|Write|MultiEdit): run detekt for the Gradle project owning the edited .kt file.

Only findings in the edited file are reported (exit 2 -> shown to Claude), so existing issues
elsewhere don't drown the feedback. Non-Kotlin files and files outside a project's src/ are ignored.
"""

import json
import pathlib
import re
import subprocess
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gradle_modules  # noqa: E402

FINDING = re.compile(r"^[ew]: (?P<path>/.+?\.kts?):(?P<line>\d+):(?P<col>\d+) (?P<message>.*)$")


def main() -> int:
    event = json.load(sys.stdin)
    file_path = event.get("tool_input", {}).get("file_path", "")
    if not file_path.endswith(".kt"):
        return 0
    edited = pathlib.Path(file_path).resolve()
    root = gradle_modules.git_toplevel(edited)
    if root is None or not edited.is_file():
        return 0
    module = gradle_modules.owning_module(edited.relative_to(root).as_posix(), gradle_modules.module_dirs(root))
    if module is None:
        return 0

    task = gradle_modules.task_path(module, "detekt")
    run = subprocess.run(
        [str(root / "gradlew"), "-q", "--console=plain", task],
        cwd=root, capture_output=True, text=True,
    )
    findings = [
        f"{edited.name}:{m['line']}:{m['col']} {m['message']}"
        for m in map(FINDING.match, (run.stdout + run.stderr).splitlines())
        if m and pathlib.Path(m["path"]).resolve() == edited
    ]
    if findings:
        print(f"detekt ({task}) found {len(findings)} issue(s) in {edited.relative_to(root)}:", file=sys.stderr)
        print("\n".join(findings), file=sys.stderr)
        print("Fix them in this file (the rule name is in brackets; don't change detekt.yml or the baseline).", file=sys.stderr)
        return 2
    if run.returncode != 0 and "Analysis failed" not in run.stdout + run.stderr:
        # detekt itself broke (config, Gradle) - tell the user, don't block the agent on it.
        print(json.dumps({"systemMessage": f"detekt hook: {task} failed to run:\n" + run.stderr[-1500:]}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
