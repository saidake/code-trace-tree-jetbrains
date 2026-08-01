#!/usr/bin/env python3
"""
Code Trace Tree ops for Claude: search / add / move / delete / rebind.

LINE nodes are identified by [file, line, trimmed-content].
Claude never passes totalOccurrences / occurrenceIndex — this script computes them.
After disk edits, run `rebind` so line numbers stay aligned (DocumentListener will not fire).
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import uuid
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, List, Optional, Sequence, Tuple


# ---------------------------------------------------------------------------
# Resolve project + storage
# ---------------------------------------------------------------------------


def find_project_root(start: Path) -> Path:
    cur = start.resolve()
    if cur.is_file():
        cur = cur.parent
    while True:
        if (cur / ".idea").is_dir() or (cur / ".vscode").is_dir() or (cur / ".git").exists():
            return cur
        if cur.parent == cur:
            raise SystemExit(f"ERROR: could not locate project root from {start}")
        cur = cur.parent


def global_app_dir() -> Path:
    if sys.platform == "darwin":
        return Path.home() / "Library" / "Application Support" / "code-trace-tree"
    if sys.platform == "win32":
        base = os.environ.get("LOCALAPPDATA") or str(Path.home() / "AppData" / "Local")
        return Path(base) / "code-trace-tree"
    base = os.environ.get("XDG_CONFIG_HOME") or str(Path.home() / ".config")
    return Path(base) / "code-trace-tree"


def read_project_id(project_root: Path) -> str:
    for rel in (".idea/code-trace-tree.project.id", ".vscode/code-trace-tree.project.id"):
        p = project_root / rel
        if p.is_file():
            return p.read_text(encoding="utf-8").strip()
    return ""


def xml_tag_text(path: Path, tag: str) -> str:
    try:
        root = ET.parse(path).getroot()
    except ET.ParseError:
        return ""
    el = root.find(tag)
    return (el.text or "").strip() if el is not None else ""


def normalize_path_key(p: str) -> str:
    s = p.replace("\\", "/").rstrip("/")
    if sys.platform == "win32":
        return s.lower()
    return s


def resolve_storage(project_root: Path) -> Path:
    app_dir = global_app_dir()
    project_id = read_project_id(project_root)
    xmls = sorted(app_dir.glob("*.xml")) if app_dir.is_dir() else []

    if project_id:
        for xml in xmls:
            if xml_tag_text(xml, "projectId") == project_id:
                return xml

    target = normalize_path_key(str(project_root))
    for xml in xmls:
        stored = xml_tag_text(xml, "path")
        if stored and normalize_path_key(stored) == target:
            return xml

    raise SystemExit(
        "ERROR: no Code Trace Tree storage XML found. "
        "Open the project once in the IDE with the plugin installed."
    )


def norm_rel(path: str) -> str:
    return path.replace("\\", "/").strip().lstrip("./")


# ---------------------------------------------------------------------------
# Locators
# ---------------------------------------------------------------------------


@dataclass(frozen=True)
class LineLocator:
    file: str
    line: int
    content: str

    @staticmethod
    def from_parts(file: str, line: int, content: str) -> "LineLocator":
        return LineLocator(norm_rel(file), int(line), content.strip())

    @staticmethod
    def from_json_item(item: Any) -> "LineLocator":
        if not isinstance(item, (list, tuple)) or len(item) != 3:
            raise SystemExit(
                f"ERROR: LINE locator must be [file, line, content], got {item!r}"
            )
        return LineLocator.from_parts(str(item[0]), int(item[1]), str(item[2]))


def parse_parent_path(raw: Optional[str]) -> List[LineLocator]:
    if raw is None or raw.strip() == "":
        return []
    try:
        data = json.loads(raw)
    except json.JSONDecodeError as e:
        raise SystemExit(f"ERROR: invalid --parent JSON: {e}") from e
    if not isinstance(data, list):
        raise SystemExit("ERROR: --parent must be a JSON array of [file, line, content]")
    return [LineLocator.from_json_item(item) for item in data]


# ---------------------------------------------------------------------------
# Occurrences (script-only; Claude never supplies these)
# ---------------------------------------------------------------------------


def read_source_lines(project_root: Path, rel_file: str) -> Optional[List[str]]:
    abs_file = project_root / rel_file
    if not abs_file.is_file():
        return None
    try:
        text = abs_file.read_text(encoding="utf-8")
    except UnicodeDecodeError:
        text = abs_file.read_text(encoding="utf-8", errors="replace")
    return text.splitlines()


def match_lines(lines: Sequence[str], content: str) -> List[int]:
    content = content.strip()
    return [i + 1 for i, ln in enumerate(lines) if ln.strip() == content]


def compute_occurrences(
    project_root: Path, rel_file: str, line: int, content: str
) -> Tuple[int, int]:
    content = content.strip()
    lines = read_source_lines(project_root, rel_file)
    if lines is None:
        raise SystemExit(f"ERROR: source file not found: {rel_file}")
    if line < 1 or line > len(lines):
        raise SystemExit(f"ERROR: line {line} out of range for {rel_file} (1..{len(lines)})")
    actual = lines[line - 1].strip()
    if actual != content:
        raise SystemExit(
            f"ERROR: line {line} in {rel_file} is {actual!r}, expected {content!r}"
        )
    matches = match_lines(lines, content)
    total = len(matches)
    index = matches.index(line) + 1
    return total, index


@dataclass
class RebindResult:
    status: str  # updated | unchanged | invalid
    id: str
    file: str
    old_line: int
    new_line: int
    content: str
    total_occurrences: int
    occurrence_index: int
    reason: str


def rebind_line_locator(
    lines: Optional[Sequence[str]],
    node_id: str,
    rel_file: str,
    old_line: int,
    content: str,
    old_total: int,
    old_index: int,
) -> Tuple[RebindResult, Optional[Tuple[int, int, int]]]:
    """
    Apply shared rebind rules.
    Returns (result, (new_line, total, index) or None if invalid/unwritable).
    """
    content = content.strip()
    if lines is None:
        return (
            RebindResult(
                "invalid",
                node_id,
                rel_file,
                old_line,
                old_line,
                content,
                0,
                0,
                "file_missing",
            ),
            None,
        )

    matches = match_lines(lines, content)
    total = len(matches)

    if not matches:
        return (
            RebindResult(
                "invalid",
                node_id,
                rel_file,
                old_line,
                old_line,
                content,
                0,
                0,
                "content_gone",
            ),
            None,
        )

    # 1) Still exact at old line
    if 1 <= old_line <= len(lines) and lines[old_line - 1].strip() == content:
        new_line = old_line
        new_index = matches.index(new_line) + 1
        reason = "exact"
    # 2) Unique content
    elif total == 1:
        new_line = matches[0]
        new_index = 1
        reason = "unique"
    # 3) Stable occurrence count + index
    elif total == old_total and 1 <= old_index <= total:
        new_line = matches[old_index - 1]
        new_index = old_index
        reason = "stable_occurrence"
    # 4) Nearest match to old line
    else:
        new_line = min(matches, key=lambda m: abs(m - old_line))
        new_index = matches.index(new_line) + 1
        reason = "nearest"

    values = (new_line, total, new_index)
    if new_line == old_line and total == old_total and new_index == old_index:
        return (
            RebindResult(
                "unchanged",
                node_id,
                rel_file,
                old_line,
                new_line,
                content,
                total,
                new_index,
                reason,
            ),
            values,
        )
    return (
        RebindResult(
            "updated",
            node_id,
            rel_file,
            old_line,
            new_line,
            content,
            total,
            new_index,
            reason,
        ),
        values,
    )


# ---------------------------------------------------------------------------
# XML tree helpers
# ---------------------------------------------------------------------------


def child_text(el: ET.Element, tag: str, default: str = "") -> str:
    c = el.find(tag)
    if c is None or c.text is None:
        return default
    return c.text.strip()


def set_child_text(el: ET.Element, tag: str, value: str) -> None:
    c = el.find(tag)
    if c is None:
        c = ET.SubElement(el, tag)
    c.text = value


def ensure_child(el: ET.Element, tag: str) -> ET.Element:
    c = el.find(tag)
    if c is None:
        c = ET.SubElement(el, tag)
    return c


def iter_nodes(container: ET.Element) -> Iterable[ET.Element]:
    """Yield tracePointNode elements under a roots list or children list."""
    for node in list(container.findall("tracePointNode")):
        yield node


def walk_tree(
    roots_el: ET.Element, depth: int = 0, parent_id: str = ""
) -> Iterable[Tuple[ET.Element, int, str, ET.Element]]:
    """
    Yield (node, depth, parent_id, container) for every node.
    container is the element that directly holds this node (tracePointNodes or children).
    """
    for node in iter_nodes(roots_el):
        yield node, depth, parent_id, roots_el
        nid = child_text(node, "id")
        children = node.find("children")
        if children is not None:
            yield from walk_tree(children, depth + 1, nid)


def node_trace(node: ET.Element) -> ET.Element:
    tp = node.find("tracePoint")
    if tp is None:
        raise SystemExit(f"ERROR: node {child_text(node, 'id')} missing <tracePoint>")
    return tp


def matches_line_locator(node: ET.Element, loc: LineLocator) -> bool:
    tp = node_trace(node)
    if child_text(tp, "traceType") != "LINE":
        return False
    if norm_rel(child_text(tp, "tracePath")) != loc.file:
        return False
    if child_text(tp, "lineNumber") != str(loc.line):
        return False
    return child_text(tp, "lineContent") == loc.content


def matches_path_locator(node: ET.Element, path: str, type_filter: Optional[str]) -> bool:
    tp = node_trace(node)
    kind = child_text(tp, "traceType")
    if type_filter and kind != type_filter:
        return False
    if kind not in ("FILE", "DIRECTORY"):
        return False
    return norm_rel(child_text(tp, "tracePath")) == norm_rel(path)


def find_by_id(roots_el: ET.Element, node_id: str) -> Tuple[ET.Element, ET.Element, str]:
    matches = [(n, c, p) for n, _, p, c in walk_tree(roots_el) if child_text(n, "id") == node_id]
    if not matches:
        raise SystemExit(f"ERROR: no node with id {node_id}")
    if len(matches) > 1:
        raise SystemExit(f"ERROR: duplicate id {node_id}")
    return matches[0]


def find_by_line_locator(
    roots_el: ET.Element, loc: LineLocator
) -> Tuple[ET.Element, ET.Element, str]:
    matches = [(n, c, p) for n, _, p, c in walk_tree(roots_el) if matches_line_locator(n, loc)]
    if not matches:
        raise SystemExit(
            f"ERROR: no LINE node matching [{loc.file!r}, {loc.line}, {loc.content!r}]"
        )
    if len(matches) > 1:
        ids = ", ".join(child_text(n, "id") for n, _, _ in matches)
        raise SystemExit(
            f"ERROR: multiple LINE nodes match [{loc.file!r}, {loc.line}, {loc.content!r}]: {ids}"
        )
    return matches[0]


def resolve_parent_path(
    roots_el: ET.Element, path: Sequence[LineLocator]
) -> Optional[ET.Element]:
    """Return immediate parent node element, or None for root placement."""
    if not path:
        return None
    current: Optional[ET.Element] = None
    for i, loc in enumerate(path):
        if current is None:
            matches = [n for n, _, _, _ in walk_tree(roots_el) if matches_line_locator(n, loc)]
        else:
            children = current.find("children")
            if children is None:
                matches = []
            else:
                matches = [n for n in iter_nodes(children) if matches_line_locator(n, loc)]
        if not matches:
            raise SystemExit(
                f"ERROR: parent path step {i} not found: [{loc.file!r}, {loc.line}, {loc.content!r}]"
            )
        if len(matches) > 1:
            raise SystemExit(
                f"ERROR: parent path step {i} is ambiguous: [{loc.file!r}, {loc.line}, {loc.content!r}]"
            )
        current = matches[0]
    return current


def collect_descendant_ids(node: ET.Element) -> List[str]:
    ids = [child_text(node, "id")]
    children = node.find("children")
    if children is not None:
        for child in iter_nodes(children):
            ids.extend(collect_descendant_ids(child))
    return ids


def detach_node(container: ET.Element, node: ET.Element) -> None:
    container.remove(node)


def attach_under(parent: Optional[ET.Element], roots_el: ET.Element, node: ET.Element) -> None:
    parent_id = child_text(parent, "id") if parent is not None else ""
    set_child_text(node, "parentId", parent_id)
    if parent is None:
        roots_el.append(node)
    else:
        children = ensure_child(parent, "children")
        children.append(node)


def get_or_create_profile(root: ET.Element, name: str) -> ET.Element:
    profiles = ensure_child(root, "traceProfiles")
    for profile in profiles.findall("traceProfile"):
        if child_text(profile, "name") == name:
            return profile
    profile = ET.SubElement(profiles, "traceProfile")
    set_child_text(profile, "name", name)
    ET.SubElement(profile, "tracePointNodes")
    ET.SubElement(profile, "expandedTracePointIds")
    return profile


def resolve_profile_name(root: ET.Element, override: Optional[str]) -> str:
    if override:
        return override
    assist = child_text(root, "claudeAssistEnabled").lower() == "true"
    target = child_text(root, "claudeAssistTarget", "CURRENT").upper()
    if assist and target == "CLAUDE":
        return "CLAUDE"
    active = child_text(root, "activeProfileName", "main")
    return active or "main"


def profile_roots(profile: ET.Element) -> ET.Element:
    return ensure_child(profile, "tracePointNodes")


def build_line_node(
    project_root: Path,
    loc: LineLocator,
    parent_id: str,
    name: str,
    description: str,
) -> ET.Element:
    total, index = compute_occurrences(project_root, loc.file, loc.line, loc.content)
    node_id = str(uuid.uuid4())
    node = ET.Element("tracePointNode")
    set_child_text(node, "id", node_id)
    set_child_text(node, "parentId", parent_id)
    tp = ET.SubElement(node, "tracePoint")
    set_child_text(tp, "traceName", name)
    set_child_text(tp, "traceType", "LINE")
    set_child_text(tp, "baseName", Path(loc.file).name)
    set_child_text(tp, "tracePath", loc.file)
    set_child_text(tp, "lineNumber", str(loc.line))
    set_child_text(tp, "lineContent", loc.content)
    set_child_text(tp, "totalOccurrences", str(total))
    set_child_text(tp, "occurrenceIndex", str(index))
    if description:
        set_child_text(tp, "description", description)
    return node


def build_path_node(
    project_root: Path,
    rel_path: str,
    kind: str,
    parent_id: str,
    name: str,
    description: str,
) -> ET.Element:
    abs_path = project_root / rel_path
    if kind == "FILE" and not abs_path.is_file():
        raise SystemExit(f"ERROR: file not found: {rel_path}")
    if kind == "DIRECTORY" and not abs_path.is_dir():
        raise SystemExit(f"ERROR: directory not found: {rel_path}")
    node_id = str(uuid.uuid4())
    node = ET.Element("tracePointNode")
    set_child_text(node, "id", node_id)
    set_child_text(node, "parentId", parent_id)
    tp = ET.SubElement(node, "tracePoint")
    set_child_text(tp, "traceName", name)
    set_child_text(tp, "traceType", kind)
    set_child_text(tp, "baseName", Path(rel_path).name)
    set_child_text(tp, "tracePath", norm_rel(rel_path))
    if description:
        set_child_text(tp, "description", description)
    return node


def infer_path_kind(project_root: Path, rel_path: str) -> str:
    abs_path = project_root / rel_path
    if abs_path.is_dir():
        return "DIRECTORY"
    if abs_path.is_file():
        return "FILE"
    raise SystemExit(f"ERROR: path not found: {rel_path}")


def bump_updated_at(root: ET.Element) -> None:
    set_child_text(root, "updatedAt", str(int(time.time() * 1000)))


def write_atomic(tree: ET.ElementTree, storage_xml: Path) -> None:
    tmp = storage_xml.with_suffix(storage_xml.suffix + ".tmp")
    if hasattr(ET, "indent"):
        ET.indent(tree.getroot(), space="  ")
    tree.write(tmp, encoding="utf-8", xml_declaration=True)
    os.replace(tmp, storage_xml)


def request_refresh(project_root: Path) -> None:
    idea = project_root / ".idea"
    idea.mkdir(parents=True, exist_ok=True)
    req = idea / "code-trace-tree.refresh-request"
    req.write_text(str(int(time.time() * 1000)) + "\n", encoding="utf-8")


def load_context(project: Optional[str], profile: Optional[str]):
    start = Path(project or ".")
    project_root = find_project_root(start)
    storage_xml = resolve_storage(project_root)
    tree = ET.parse(storage_xml)
    root = tree.getroot()
    profile_name = resolve_profile_name(root, profile)
    profile_el = get_or_create_profile(root, profile_name)
    if (
        child_text(root, "claudeAssistEnabled").lower() == "true"
        and child_text(root, "claudeAssistTarget", "CURRENT").upper() == "CLAUDE"
        and not profile
    ):
        set_child_text(root, "activeProfileName", "CLAUDE")
    roots_el = profile_roots(profile_el)
    return project_root, storage_xml, tree, root, profile_name, roots_el


def node_to_row(node: ET.Element, depth: int, parent_id: str) -> dict:
    tp = node_trace(node)
    children = node.find("children")
    child_count = len(list(iter_nodes(children))) if children is not None else 0
    return {
        "id": child_text(node, "id"),
        "parentId": parent_id,
        "type": child_text(tp, "traceType"),
        "file": child_text(tp, "tracePath"),
        "line": child_text(tp, "lineNumber") or "",
        "content": child_text(tp, "lineContent"),
        "name": child_text(tp, "traceName"),
        "depth": depth,
        "childCount": child_count,
    }


# ---------------------------------------------------------------------------
# Commands
# ---------------------------------------------------------------------------


def cmd_search(args: argparse.Namespace) -> int:
    project_root, storage_xml, tree, root, profile_name, roots_el = load_context(
        args.project, args.profile
    )
    rows = []
    for node, depth, parent_id, _ in walk_tree(roots_el):
        tp = node_trace(node)
        kind = child_text(tp, "traceType")
        if args.type and kind != args.type:
            continue
        nid = child_text(node, "id")
        if args.id and nid != args.id:
            continue
        path = norm_rel(child_text(tp, "tracePath"))
        if args.file and path != norm_rel(args.file):
            continue
        if args.line is not None and child_text(tp, "lineNumber") != str(args.line):
            continue
        content = child_text(tp, "lineContent")
        if args.content is not None and args.content not in content:
            continue
        name = child_text(tp, "traceName")
        if args.name is not None and args.name not in name:
            continue
        rows.append(node_to_row(node, depth, parent_id))

    print(
        json.dumps(
            {
                "project_root": str(project_root),
                "storage_xml": str(storage_xml),
                "profile": profile_name,
                "matches": rows,
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


def cmd_add(args: argparse.Namespace) -> int:
    project_root, storage_xml, tree, root, profile_name, roots_el = load_context(
        args.project, args.profile
    )
    parent_path = parse_parent_path(args.parent)
    parent = resolve_parent_path(roots_el, parent_path)
    parent_id = child_text(parent, "id") if parent is not None else ""

    kind = (args.type or "LINE").upper()
    if kind == "LINE":
        if not args.file or args.line is None or args.content is None:
            raise SystemExit("ERROR: LINE add requires --file, --line, and --content")
        loc = LineLocator.from_parts(args.file, args.line, args.content)
        # Fail early if identical locator already exists
        existing = [n for n, _, _, _ in walk_tree(roots_el) if matches_line_locator(n, loc)]
        if existing:
            raise SystemExit(
                f"ERROR: LINE node already exists: {child_text(existing[0], 'id')}"
            )
        node = build_line_node(
            project_root, loc, parent_id, args.name or "", args.description or ""
        )
    elif kind in ("FILE", "DIRECTORY"):
        path = args.file
        if not path:
            raise SystemExit(f"ERROR: {kind} add requires --file (path)")
        node = build_path_node(
            project_root,
            norm_rel(path),
            kind,
            parent_id,
            args.name or "",
            args.description or "",
        )
    else:
        raise SystemExit(f"ERROR: unknown --type {kind}")

    if args.dry_run:
        print(
            json.dumps(
                {
                    "action": "add",
                    "dry_run": True,
                    "profile": profile_name,
                    "parentId": parent_id,
                    "node": node_to_row(node, 0, parent_id),
                },
                indent=2,
                ensure_ascii=False,
            )
        )
        return 0

    attach_under(parent, roots_el, node)
    bump_updated_at(root)
    write_atomic(tree, storage_xml)
    if not args.no_refresh:
        request_refresh(project_root)

    print(
        json.dumps(
            {
                "action": "add",
                "profile": profile_name,
                "storage_xml": str(storage_xml),
                "node": node_to_row(node, 0, parent_id),
                "refreshed": not args.no_refresh,
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


def resolve_target_node(
    roots_el: ET.Element, args: argparse.Namespace
) -> Tuple[ET.Element, ET.Element, str]:
    if args.id:
        return find_by_id(roots_el, args.id)
    if args.file and args.line is not None and args.content is not None:
        loc = LineLocator.from_parts(args.file, args.line, args.content)
        return find_by_line_locator(roots_el, loc)
    if args.file and args.line is None and args.content is None:
        path = norm_rel(args.file)
        matches = [
            (n, c, p)
            for n, _, p, c in walk_tree(roots_el)
            if matches_path_locator(n, path, None)
        ]
        if not matches:
            raise SystemExit(f"ERROR: no FILE/DIRECTORY node matching path {path!r}")
        if len(matches) > 1:
            ids = ", ".join(child_text(n, "id") for n, _, _ in matches)
            raise SystemExit(f"ERROR: multiple path nodes match {path!r}: {ids}")
        return matches[0]
    raise SystemExit("ERROR: provide --id or LINE locator (--file --line --content)")


def cmd_move(args: argparse.Namespace) -> int:
    project_root, storage_xml, tree, root, profile_name, roots_el = load_context(
        args.project, args.profile
    )
    node, container, _old_parent = resolve_target_node(roots_el, args)
    parent_path = parse_parent_path(args.parent)
    new_parent = resolve_parent_path(roots_el, parent_path)

    moved_ids = set(collect_descendant_ids(node))
    if new_parent is not None and child_text(new_parent, "id") in moved_ids:
        raise SystemExit("ERROR: cannot move a node under itself or its descendant")

    if args.dry_run:
        print(
            json.dumps(
                {
                    "action": "move",
                    "dry_run": True,
                    "profile": profile_name,
                    "id": child_text(node, "id"),
                    "newParentId": child_text(new_parent, "id") if new_parent else "",
                },
                indent=2,
            )
        )
        return 0

    detach_node(container, node)
    attach_under(new_parent, roots_el, node)
    bump_updated_at(root)
    write_atomic(tree, storage_xml)
    if not args.no_refresh:
        request_refresh(project_root)

    print(
        json.dumps(
            {
                "action": "move",
                "profile": profile_name,
                "id": child_text(node, "id"),
                "newParentId": child_text(new_parent, "id") if new_parent else "",
                "refreshed": not args.no_refresh,
            },
            indent=2,
        )
    )
    return 0


def cmd_delete(args: argparse.Namespace) -> int:
    project_root, storage_xml, tree, root, profile_name, roots_el = load_context(
        args.project, args.profile
    )
    node, container, _ = resolve_target_node(roots_el, args)
    deleted = collect_descendant_ids(node)

    if args.dry_run:
        print(
            json.dumps(
                {
                    "action": "delete",
                    "dry_run": True,
                    "profile": profile_name,
                    "deletedIds": deleted,
                },
                indent=2,
            )
        )
        return 0

    detach_node(container, node)
    # Drop empty <children> containers left behind when removing last child — optional cleanup
    bump_updated_at(root)
    write_atomic(tree, storage_xml)
    if not args.no_refresh:
        request_refresh(project_root)

    print(
        json.dumps(
            {
                "action": "delete",
                "profile": profile_name,
                "deletedIds": deleted,
                "refreshed": not args.no_refresh,
            },
            indent=2,
        )
    )
    return 0


def cmd_rebind(args: argparse.Namespace) -> int:
    project_root, storage_xml, tree, root, profile_name, roots_el = load_context(
        args.project, args.profile
    )
    file_filters = {norm_rel(f) for f in (args.file or [])}

    updated: List[dict] = []
    invalid: List[dict] = []
    unchanged = 0
    dirty = False

    for node, _, _, _ in walk_tree(roots_el):
        tp = node_trace(node)
        if child_text(tp, "traceType") != "LINE":
            continue
        rel_file = norm_rel(child_text(tp, "tracePath"))
        if file_filters and rel_file not in file_filters:
            continue

        old_line = int(child_text(tp, "lineNumber") or "0")
        content = child_text(tp, "lineContent")
        old_total = int(child_text(tp, "totalOccurrences") or "0")
        old_index = int(child_text(tp, "occurrenceIndex") or "0")
        node_id = child_text(node, "id")

        lines = read_source_lines(project_root, rel_file)
        result, values = rebind_line_locator(
            lines, node_id, rel_file, old_line, content, old_total, old_index
        )
        row = {
            "id": result.id,
            "file": result.file,
            "oldLine": result.old_line,
            "newLine": result.new_line,
            "content": result.content,
            "totalOccurrences": result.total_occurrences,
            "occurrenceIndex": result.occurrence_index,
            "reason": result.reason,
        }
        if result.status == "invalid":
            invalid.append(row)
            continue
        assert values is not None
        new_line, total, index = values
        if result.status == "unchanged":
            # Still refresh occurrence fields if XML was stale but locator equal
            if (
                child_text(tp, "totalOccurrences") != str(total)
                or child_text(tp, "occurrenceIndex") != str(index)
            ):
                if not args.dry_run:
                    set_child_text(tp, "totalOccurrences", str(total))
                    set_child_text(tp, "occurrenceIndex", str(index))
                    dirty = True
                updated.append({**row, "reason": "refresh_occurrences"})
            else:
                unchanged += 1
            continue

        if not args.dry_run:
            set_child_text(tp, "lineNumber", str(new_line))
            set_child_text(tp, "totalOccurrences", str(total))
            set_child_text(tp, "occurrenceIndex", str(index))
            dirty = True
        updated.append(row)

    if dirty and not args.dry_run:
        bump_updated_at(root)
        write_atomic(tree, storage_xml)
        if not args.no_refresh:
            request_refresh(project_root)

    print(
        json.dumps(
            {
                "action": "rebind",
                "dry_run": bool(args.dry_run),
                "profile": profile_name,
                "storage_xml": str(storage_xml),
                "updated": updated,
                "invalid": invalid,
                "unchanged": unchanged,
                "refreshed": dirty and not args.dry_run and not args.no_refresh,
            },
            indent=2,
            ensure_ascii=False,
        )
    )
    return 0


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def add_shared_flags(p: argparse.ArgumentParser) -> None:
    p.add_argument("--project", help="Project path (default: cwd)")
    p.add_argument("--profile", help="Profile name override")
    p.add_argument("--dry-run", action="store_true", help="Do not write XML or refresh")
    p.add_argument("--no-refresh", action="store_true", help="Skip IDE refresh-request")


def add_locator_flags(p: argparse.ArgumentParser, required_line: bool = False) -> None:
    p.add_argument("--id", help="Node UUID")
    p.add_argument("--file", help="Relative file/directory path")
    p.add_argument("--line", type=int, help="1-based line number (LINE)")
    p.add_argument("--content", help="Trimmed line content (LINE)")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Search / add / move / delete / rebind Code Trace Tree nodes (no occurrence args)."
    )
    sub = parser.add_subparsers(dest="command", required=True)

    p_search = sub.add_parser("search", help="Find nodes in the target profile")
    add_shared_flags(p_search)
    p_search.add_argument("--id")
    p_search.add_argument("--file")
    p_search.add_argument("--line", type=int)
    p_search.add_argument("--content", help="Substring match on lineContent")
    p_search.add_argument("--name", help="Substring match on traceName")
    p_search.add_argument("--type", choices=["LINE", "FILE", "DIRECTORY"])
    p_search.set_defaults(func=cmd_search)

    p_add = sub.add_parser("add", help="Add a node under an optional parent path")
    add_shared_flags(p_add)
    p_add.add_argument("pos_file", nargs="?", help="Positional file (LINE/FILE/DIRECTORY)")
    p_add.add_argument("pos_line", nargs="?", type=int, help="Positional line (LINE)")
    p_add.add_argument("pos_content", nargs="?", help="Positional trimmed content (LINE)")
    p_add.add_argument("--file")
    p_add.add_argument("--line", type=int)
    p_add.add_argument("--content")
    p_add.add_argument(
        "--parent",
        default="[]",
        help='JSON parent path: [["file",line,"content"], ...]',
    )
    p_add.add_argument("--name", default="")
    p_add.add_argument("--description", default="")
    p_add.add_argument("--type", choices=["LINE", "FILE", "DIRECTORY"], default=None)
    p_add.set_defaults(func=cmd_add)

    p_move = sub.add_parser("move", help="Reparent a node (subtree moves with it)")
    add_shared_flags(p_move)
    add_locator_flags(p_move)
    p_move.add_argument(
        "--parent",
        required=True,
        help='JSON parent path (use [] for root): [["file",line,"content"], ...]',
    )
    p_move.set_defaults(func=cmd_move)

    p_delete = sub.add_parser("delete", help="Delete a node and its subtree")
    add_shared_flags(p_delete)
    add_locator_flags(p_delete)
    p_delete.set_defaults(func=cmd_delete)

    p_rebind = sub.add_parser(
        "rebind",
        help="Repair LINE lineNumbers after disk edits (content-based; no occurrence args)",
    )
    add_shared_flags(p_rebind)
    p_rebind.add_argument(
        "--file",
        action="append",
        default=[],
        help="Limit to relative path(s); repeatable. Default: all LINE nodes in profile.",
    )
    p_rebind.set_defaults(func=cmd_rebind)

    return parser


def normalize_add_args(args: argparse.Namespace) -> None:
    if args.command != "add":
        return
    if args.file is None and args.pos_file is not None:
        args.file = args.pos_file
    if args.line is None and args.pos_line is not None:
        args.line = args.pos_line
    if args.content is None and args.pos_content is not None:
        args.content = args.pos_content
    if args.type is None:
        if args.line is not None or args.content is not None:
            args.type = "LINE"
        elif args.file:
            # Infer from filesystem later in cmd_add via build; set FILE default then fix
            args.type = "LINE" if args.line is not None else None


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    normalize_add_args(args)
    if args.command == "add" and args.type is None and args.file:
        # Infer FILE vs DIRECTORY when no line/content
        start = Path(args.project or ".")
        try:
            project_root = find_project_root(start)
            args.type = infer_path_kind(project_root, norm_rel(args.file))
        except SystemExit:
            args.type = "FILE"
    try:
        return args.func(args)
    except BrokenPipeError:
        return 0


if __name__ == "__main__":
    sys.exit(main())
