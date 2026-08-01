---
name: code-trace-tree
description: >
  Read, edit, and refresh Code Trace Tree plugin data (JetBrains / VS Code shared storage).
  Use when the user asks to add/update/remove trace points (line, file, or directory), inspect or
  modify Code Trace Tree profiles, sync agent-written traces into the IDE, notify IntelliJ IDEA
  to reload plugin data, or select/navigate to trace points in the IDE tree.
  When `<claudeAssistEnabled>` is true, auto-sync topic-related traces each turn that touched code.
---

# Code Trace Tree

Operate the hybrid storage used by the Code Trace Tree IDE plugins, then ask the IDE to reload.

## Storage layout

| Piece | Location |
|-------|----------|
| Project id | `.idea/code-trace-tree.project.id` (prefer) or `.vscode/code-trace-tree.project.id` |
| Global XML | OS config dir + `/code-trace-tree/<FolderName>.xml` |
| Refresh signal | `.idea/code-trace-tree.refresh-request` |
| Select signal | `.idea/code-trace-tree.select-request` (one node UUID per line) |

Global base directory:

- Windows: `%LOCALAPPDATA%\code-trace-tree`
- macOS: `~/Library/Application Support/code-trace-tree`
- Linux: `$XDG_CONFIG_HOME/code-trace-tree` or `~/.config/code-trace-tree`

Resolve the bound XML with:

```bash
# macOS / Linux
bash scripts/resolve_storage.sh
# optional: bash scripts/resolve_storage.sh /path/to/project
```

```bat
REM Windows
scripts\resolve_storage.bat
REM optional: scripts\resolve_storage.bat C:\path\to\project
```

## Preferred code workflow format

* When generating a code workflow, trace points with parent-child relationships should follow a clear nesting structure.
  For example, if the parent node represents a method, its direct child nodes should represent methods called within that method, and their direct child nodes should point to the corresponding method definitions.
  Example:
       method A definition
         - method B call
           - method B definition

* Keep trace point names simple and concise. Add descriptions only when additional context is needed.

## Content matching and `isValid`

`isValid` is never stored. On load/reload:

| `traceType` | Valid when |
|--------------|------------|
| `LINE` | Path is a file and trimmed line at `lineNumber` matches `lineContent`, or occurrence rebinding succeeds |
| `FILE` | Path exists and is a file |
| `DIRECTORY` | Path exists and is a directory |

For `LINE` nodes, set accurate trimmed `lineContent`, `lineNumber`, `totalOccurrences`, and `occurrenceIndex` so the plugin can re-bind after code moves. Details: [references/data-format.md](references/data-format.md).

## Safe operations

| Goal | How |
|------|-----|
| List traces | Parse active profile’s `<tracePointNodes>` |
| Add root | Append a root `<tracePointNode>` with empty `<parentId>` |
| Add child | Append under parent’s `<children>`, set `<parentId>` |
| Update location | Change `tracePath` / `baseName` / `traceName` / (`lineNumber` / `lineContent` for `LINE`) |
| Remove node | Delete the node and its `<children>` subtree |
| Switch profile | Set `<activeProfileName>` to an existing profile `<name>` |

## After refresh

IntelliJ (with the plugin loaded) reloads the bound XML, refreshes the Code Trace Tree tool window, and re-applies highlights. The plugin deletes `.idea/code-trace-tree.refresh-request` after a successful reload.

## Additional resources

- XML schema details: [references/data-format.md](references/data-format.md)
- Resolve storage: `scripts/resolve_storage.sh` (macOS/Linux) or `scripts/resolve_storage.bat` (Windows)
- Request IDE refresh: `scripts/request_refresh.sh` (macOS/Linux) or `scripts/request_refresh.bat` (Windows)
- Select / navigate: `scripts/select_trace_points.sh` (macOS/Linux) or `scripts/select_trace_points.bat` (Windows)

## Edit plugin data action

1. **Resolve** the project id + global XML (`resolve_storage.sh` / `resolve_storage.bat`).
2. **Read** the XML. Schema: [references/data-format.md](references/data-format.md).
3. **Edit** carefully (see rules below). Prefer atomic write: write `*.xml.tmp` then replace.
4. **Refresh IDE** so IntelliJ reloads in-memory state:

```bash
# macOS / Linux
bash scripts/request_refresh.sh
```

```bat
REM Windows
scripts\request_refresh.bat
```

Editing the global XML alone is usually enough (the plugin watches it). Always write the refresh request after agent edits so reload is explicit.

## Claude Assist action

Check project XML flags after resolving storage:

| Flag | Meaning |
|------|---------|
| `claudeAssistEnabled` | `true` → Claude may mutate traces; `false`/missing → do **not** auto-sync |
| `claudeAssistTarget` | `CURRENT` → edit `<activeProfileName>`; `CLAUDE` → edit/create profile named `CLAUDE` |

When **enabled** and the current turn **touched code** (read, edited, or discussed concrete source for the topic):

1. Resolve the target profile (`CURRENT` or `CLAUDE`; create `CLAUDE` if missing and set it active when using that target).
2. Add, update, or delete trace points for the **discussed topic** only (follow Preferred code workflow format).
3. Add short `<description>` notes when extra context helps; keep `traceName` concise.
4. Do not rewrite unrelated nodes or other profiles.
5. Finish with the usual refresh request (and select/navigate when a single new node should be shown).

When **disabled**, only edit traces if the user explicitly asks.

## Edit rules

- Keep `<project version="4">`, `<projectId>`, and `<path>` unless you intentionally rebind storage.
- Bump `<updatedAt>` to the current epoch milliseconds when you change content.
- Every `<tracePoint>` needs `<traceType>`: `LINE`, `FILE`, or `DIRECTORY`.
- `traceName` is the user label; `baseName` is the last path segment; `tracePath` is **relative to the project root** (forward slashes preferred).
- For `LINE`: store trimmed `lineContent`, 1-based `lineNumber`, `totalOccurrences`, and `occurrenceIndex`.
- For `FILE` / `DIRECTORY`: omit line fields; `tracePath` is the file or directory path.
- Every `<tracePointNode>` needs `<id>` (UUID) and `<parentId>` (empty for roots).
- Nest children under `<children>`; child `parentId` must equal the parent node id.
- Do **not** persist `isValid` (runtime-only).
- Do not delete unrelated profiles. Default profile name is `main`.
- If the IDE has the project open, finish XML edits **before** writing the refresh request.

## Select / navigate in the IDE action

Write node UUIDs (one per line) to `.idea/code-trace-tree.select-request`, or use the helper scripts. The plugin shows the Code Trace Tree tool window, selects and reveals those nodes, then deletes the file.

| Request | Tree | Editor |
|---------|------|--------|
| 1 valid id | Select + reveal | Navigate to source |
| 2+ valid ids | Select + reveal all | No navigation |
| Unknown ids only | No-op (file still deleted) | No navigation |

Use after creating or locating traces when the user should see them in the IDE. Prefer a single id when you want the editor to jump to the source.

```bash
# macOS / Linux
bash scripts/select_trace_points.sh <id> [id...]
```

```bat
REM Windows
scripts\select_trace_points.bat <id> [id...]
```

