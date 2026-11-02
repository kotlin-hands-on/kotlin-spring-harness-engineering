#!/usr/bin/env python3
"""PreToolUse(Edit|Write|MultiEdit) for the planner agent: allow file writes only below openspec/."""

import json
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import gradle_modules  # noqa: E402


def main() -> int:
    event = json.load(sys.stdin)
    target = pathlib.Path(event.get("tool_input", {}).get("file_path", "")).resolve()
    root = gradle_modules.git_toplevel(target)
    if root is not None and target.is_relative_to(root / "openspec"):
        return 0
    print(
        f"The planner only writes planning artifacts below openspec/ - not {target}. "
        "Put the change into the OpenSpec proposal/design/tasks; the implementer writes code.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
