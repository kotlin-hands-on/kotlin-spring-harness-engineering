#!/usr/bin/env python3
"""SessionStart: check that the tools this harness relies on are available. Silent when everything is fine.

SessionStart cannot block, so problems are reported twice: as a `systemMessage` for the user and as
`additionalContext` so the agent knows which workflow steps will not work (e.g. no jbcontext -> fall back to grep).
Only local checks, no network and no Gradle invocation, so the hook stays fast.
"""

from __future__ import annotations  # keep running on old pythons so the version check can report them

import json
import os
import pathlib
import re
import shutil
import subprocess
import sys

MIN_PYTHON = (3, 10)  # hooks use `X | None` annotations

TOOLS = {
    # name -> (executable, what breaks without it)
    "git": ("git", "all hooks (module detection, test-state fingerprints)"),
    "jbang": ("jbang", "the Gradle MCP server (.mcp.json) - fall back to ./gradlew"),
    "jbcontext": (os.environ.get("JBCONTEXT_BIN", "jbcontext"), "semantic code search - fall back to grep"),
    "openspec": ("openspec", "the planner, /ship and all opsx/openspec skills (`npm i -g @fission-ai/openspec`)"),
}


def required_jdk(root: pathlib.Path) -> int | None:
    build = root / "build.gradle.kts"
    match = re.search(r"jvmToolchain\((\d+)\)", build.read_text()) if build.is_file() else None
    return int(match.group(1)) if match else None


def jdk_homes() -> list[pathlib.Path]:
    """Locations Gradle's toolchain detection looks at by default (plus JAVA_HOME / PATH java)."""
    home = pathlib.Path.home()
    homes: list[pathlib.Path] = []
    for name, value in os.environ.items():
        if re.fullmatch(r"JAVA_HOME|JDK_?\d+|JAVA_?\d+_HOME", name) and value:
            homes.append(pathlib.Path(value))
    java = shutil.which("java")
    if java:
        homes.append(pathlib.Path(java).resolve().parent.parent)
    for parent in (
        pathlib.Path("/Library/Java/JavaVirtualMachines"),
        home / "Library/Java/JavaVirtualMachines",
        home / ".gradle/jdks",
        home / ".sdkman/candidates/java",
        home / ".asdf/installs/java",
        home / ".jdks",
        pathlib.Path("/usr/lib/jvm"),
    ):
        if parent.is_dir():
            for child in parent.iterdir():
                homes.extend([child, child / "Contents/Home"])
    return homes


def jdk_major(home: pathlib.Path) -> int | None:
    release = home / "release"
    if not release.is_file():
        return None
    match = re.search(r'^JAVA_VERSION="(\d+)', release.read_text(errors="replace"), re.M)
    return int(match.group(1)) if match else None


def main() -> int:
    event = json.load(sys.stdin)
    root = pathlib.Path(event.get("cwd") or os.environ.get("CLAUDE_PROJECT_DIR") or ".")
    toplevel = subprocess.run(["git", "-C", str(root), "rev-parse", "--show-toplevel"], capture_output=True, text=True)
    if toplevel.returncode == 0:
        root = pathlib.Path(toplevel.stdout.strip())

    problems: list[str] = []
    if sys.version_info < MIN_PYTHON:
        problems.append(f"python3 is {sys.version.split()[0]}, hooks need >= {'.'.join(map(str, MIN_PYTHON))}.")
    for name, (executable, impact) in TOOLS.items():
        if shutil.which(executable) is None:
            problems.append(f"`{name}` not found on PATH - affects {impact}.")

    wanted = required_jdk(root)
    if wanted is not None and not any(jdk_major(h) == wanted for h in jdk_homes()):
        problems.append(
            f"No JDK {wanted} found (jvmToolchain({wanted})) and no toolchain resolver plugin is configured, "
            f"so Gradle cannot provision one - every build and hook will fail. Install a JDK {wanted}."
        )

    if not os.environ.get("CONTEXT7_API_KEY"):
        problems.append("CONTEXT7_API_KEY is not set - Context7 docs lookups run unauthenticated (rate-limited).")

    if problems:
        text = "Harness preflight found problems:\n- " + "\n- ".join(problems)
        print(json.dumps({
            "systemMessage": text,
            "hookSpecificOutput": {"hookEventName": "SessionStart", "additionalContext": text},
        }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
