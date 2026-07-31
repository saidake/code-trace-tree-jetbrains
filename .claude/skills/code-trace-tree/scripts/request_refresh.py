#!/usr/bin/env python3
"""Ask IntelliJ (Code Trace Tree plugin) to reload global storage for this project."""

from __future__ import annotations

import sys
import time
from pathlib import Path


def find_project_root(start: Path) -> Path | None:
    cur = start.resolve()
    if cur.is_file():
        cur = cur.parent
    for candidate in [cur, *cur.parents]:
        if (candidate / ".idea").is_dir() or (candidate / ".vscode").is_dir():
            return candidate
        if (candidate / ".git").exists():
            return candidate
    return None


def main() -> int:
    start = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    project_root = find_project_root(start)
    if project_root is None:
        print("ERROR: could not locate project root from", start, file=sys.stderr)
        return 1

    idea_dir = project_root / ".idea"
    idea_dir.mkdir(parents=True, exist_ok=True)
    request = idea_dir / "code-trace-tree.refresh-request"
    request.write_text(f"{int(time.time() * 1000)}\n", encoding="utf-8")
    print(f"wrote={request}")
    print("IDE should reload Code Trace Tree data if the project is open with the plugin.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
