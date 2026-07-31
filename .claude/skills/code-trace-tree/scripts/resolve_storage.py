#!/usr/bin/env python3
"""Resolve Code Trace Tree project id + bound global XML for the current project."""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def global_app_dir() -> Path:
    if sys.platform.startswith("win"):
        base = os.environ.get("LOCALAPPDATA") or str(Path.home() / "AppData" / "Local")
        return Path(base) / "code-trace-tree"
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "code-trace-tree"
    xdg = os.environ.get("XDG_CONFIG_HOME")
    base = Path(xdg) if xdg else Path.home() / ".config"
    return base / "code-trace-tree"


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


def read_project_id(project_root: Path) -> str | None:
    for rel in (".idea/code-trace-tree.project.id", ".vscode/code-trace-tree.project.id"):
        path = project_root / rel
        if path.is_file():
            value = path.read_text(encoding="utf-8").strip()
            if value:
                return value
    return None


def find_xml_by_project_id(project_id: str) -> Path | None:
    app_dir = global_app_dir()
    if not app_dir.is_dir():
        return None
    for path in sorted(app_dir.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
            if root.tag != "project":
                continue
            pid = (root.findtext("projectId") or "").strip()
            if pid == project_id:
                return path
        except ET.ParseError:
            continue
    return None


def find_xml_by_path(project_root: Path) -> Path | None:
    app_dir = global_app_dir()
    if not app_dir.is_dir():
        return None
    target = str(project_root.resolve())
    if sys.platform.startswith("win"):
        target = target.lower()
    for path in sorted(app_dir.glob("*.xml")):
        try:
            root = ET.parse(path).getroot()
            if root.tag != "project":
                continue
            stored = (root.findtext("path") or "").strip()
            if sys.platform.startswith("win"):
                stored = stored.lower()
            if stored == target:
                return path
        except ET.ParseError:
            continue
    return None


def main() -> int:
    start = Path(sys.argv[1]) if len(sys.argv) > 1 else Path.cwd()
    project_root = find_project_root(start)
    if project_root is None:
        print("ERROR: could not locate project root from", start, file=sys.stderr)
        return 1

    app_dir = global_app_dir()
    project_id = read_project_id(project_root)
    xml_path = find_xml_by_project_id(project_id) if project_id else None
    if xml_path is None:
        xml_path = find_xml_by_path(project_root)

    print(f"project_root={project_root}")
    print(f"global_dir={app_dir}")
    print(f"project_id={project_id or ''}")
    print(f"storage_xml={xml_path or ''}")

    if xml_path is None:
        print(
            "ERROR: no Code Trace Tree storage XML found. "
            "Open the project once in the IDE with the plugin installed.",
            file=sys.stderr,
        )
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
