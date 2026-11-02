"""Shared helpers for the Claude Code hooks: map files to Gradle projects and fingerprint them.

Assumes the default Gradle layout: a project lives in the directory holding its build file and its
path is derived from that directory (`a/b` -> `:a:b`, repository root -> `:`). Projects relocated via
`project(":x").projectDir = ...` in settings are not detected.
"""

import hashlib
import pathlib
import subprocess

BUILD_FILES = ("build.gradle.kts", "build.gradle")
# Files outside any project's src/ that change the build for every project.
GLOBAL_PREFIXES = ("gradle/", "buildSrc/", "build-logic/")
GLOBAL_FILES = ("settings.gradle.kts", "settings.gradle", "gradle.properties") + BUILD_FILES
SKIPPED_DIRS = {"build", ".gradle", ".git", ".idea", ".claude", ".kotlin", "node_modules", "out", "buildSrc", "build-logic"}


def git_toplevel(start: pathlib.Path) -> pathlib.Path | None:
    directory = start if start.is_dir() else start.parent
    while not directory.exists():
        directory = directory.parent
    result = subprocess.run(
        ["git", "-C", str(directory), "rev-parse", "--show-toplevel"],
        capture_output=True, text=True,
    )
    return pathlib.Path(result.stdout.strip()) if result.returncode == 0 else None


def gradle_path(module_dir: str) -> str:
    """'' -> ':', 'a/b' -> ':a:b'."""
    return ":" + module_dir.replace("/", ":") if module_dir else ":"


def task_path(module_dir: str, task: str) -> str:
    return f":{task}" if not module_dir else f"{gradle_path(module_dir)}:{task}"


def module_dirs(root: pathlib.Path) -> list[str]:
    """Relative directories (posix, '' for the root) that contain a Gradle build file."""
    found = []
    stack = [root]
    while stack:
        directory = stack.pop()
        if any((directory / name).is_file() for name in BUILD_FILES):
            found.append(directory.relative_to(root).as_posix() if directory != root else "")
        stack.extend(
            child for child in directory.iterdir()
            if child.is_dir() and child.name not in SKIPPED_DIRS and not child.name.startswith(".")
        )
    return found


def owning_module(relative_file: str, modules: list[str]) -> str | None:
    """Deepest module whose src/ or build file contains the file, None if the file is not build input."""
    for module in sorted(modules, key=len, reverse=True):
        prefix = f"{module}/" if module else ""
        if not relative_file.startswith(prefix):
            continue
        rest = relative_file[len(prefix):]
        if rest.startswith("src/") or rest in BUILD_FILES:
            return module
    return None


def is_global(relative_file: str) -> bool:
    return relative_file in GLOBAL_FILES or relative_file.startswith(GLOBAL_PREFIXES)


def fingerprints(root: pathlib.Path) -> dict[str, str]:
    """Content digest per module dir over its src/ + build file, salted with all global build files.

    Only build inputs count, so edits to docs, skills, OpenSpec artifacts etc. never trigger a run.
    """
    listed = subprocess.run(
        ["git", "-C", str(root), "ls-files", "--cached", "--others", "--exclude-standard", "-z"],
        capture_output=True, text=True, check=True,
    ).stdout.split("\0")
    modules = module_dirs(root)
    global_hash = hashlib.sha256()
    per_module = {module: hashlib.sha256() for module in modules}
    for relative_file in sorted(f for f in listed if f):
        path = root / relative_file
        if not path.is_file():  # deleted but still in the index
            content = b"<deleted>"
        else:
            content = path.read_bytes()
        entry = relative_file.encode() + b"\0" + hashlib.sha256(content).digest()
        if is_global(relative_file):
            global_hash.update(entry)
        module = owning_module(relative_file, modules)
        if module is not None:
            per_module[module].update(entry)
    salt = global_hash.hexdigest()
    return {module: hashlib.sha256((salt + h.hexdigest()).encode()).hexdigest() for module, h in per_module.items()}


def state_file(root: pathlib.Path, name: str) -> pathlib.Path:
    """Per-worktree state inside .git, so `gradle clean` doesn't reset it and worktrees don't share it."""
    git_path = subprocess.run(
        ["git", "-C", str(root), "rev-parse", "--git-path", f"claude-hooks/{name}"],
        capture_output=True, text=True, check=True,
    ).stdout.strip()
    path = pathlib.Path(git_path)
    path = path if path.is_absolute() else root / path
    path.parent.mkdir(parents=True, exist_ok=True)
    return path
