---
name: code-trace-tree
description: >
  Read, edit, and refresh Code Trace Tree plugin data (JetBrains / VS Code shared storage).
  Use when the user asks to add/update/remove trace points, inspect or modify Code Trace Tree
  profiles, sync agent-written traces into the IDE, or notify IntelliJ IDEA to reload plugin data.
---

# Code Trace Tree

Operate the hybrid storage used by the Code Trace Tree IDE plugins, then ask the IDE to reload.

## Storage layout

| Piece | Location |
|-------|----------|
| Project id | `.idea/code-trace-tree.project.id` (prefer) or `.vscode/code-trace-tree.project.id` |
| Global XML | OS config dir + `/code-trace-tree/<FolderName>.xml` |
| Refresh signal | `.idea/code-trace-tree.refresh-request` |

Global base directory:

- Windows: `%LOCALAPPDATA%\code-trace-tree`
- macOS: `~/Library/Application Support/code-trace-tree`
- Linux: `$XDG_CONFIG_HOME/code-trace-tree` or `~/.config/code-trace-tree`

Resolve the bound XML with:

```bash
python scripts/resolve_storage.py
# optional: python scripts/resolve_storage.py /path/to/project
```

## Workflow

1. **Resolve** the project id + global XML (`resolve_storage.py`).
2. **Read** the XML. Schema: [references/data-format.md](references/data-format.md).
3. **Edit** carefully (see rules below). Prefer atomic write: write `*.xml.tmp` then replace.
4. **Refresh IDE** so IntelliJ reloads in-memory state:

```bash
python scripts/request_refresh.py
```

Editing the global XML alone is usually enough (the plugin watches it). Always write the refresh request after agent edits so reload is explicit.

## Edit rules

- Keep `<project version="4">`, `<projectId>`, and `<path>` unless you intentionally rebind storage.
- Bump `<updatedAt>` to the current epoch milliseconds when you change content.
- `filePath` is **relative to the project root** (forward slashes preferred).
- Every `<tracePointNode>` needs `<id>` (UUID) and `<parentId>` (empty for roots).
- Nest children under `<children>`; child `parentId` must equal the parent node id.
- Do **not** persist `isValid` (runtime-only).
- Do not delete unrelated profiles. Default profile name is `main`.
- If the IDE has the project open, finish XML edits **before** writing the refresh request.

## Safe operations

| Goal | How |
|------|-----|
| List traces | Parse active profile’s `<tracePointNodes>` |
| Add root | Append a root `<tracePointNode>` with empty `<parentId>` |
| Add child | Append under parent’s `<children>`, set `<parentId>` |
| Update location | Change `filePath` / `fileName` / `lineNumber` / `lineContent` |
| Remove node | Delete the node and its `<children>` subtree |
| Switch profile | Set `<activeProfileName>` to an existing profile `<name>` |

## After refresh

IntelliJ (with the plugin loaded) reloads the bound XML, refreshes the Code Trace Tree tool window, and re-applies highlights. The plugin deletes `.idea/code-trace-tree.refresh-request` after a successful reload.

## Additional resources

- XML schema details: [references/data-format.md](references/data-format.md)
- Resolve storage: `scripts/resolve_storage.py`
- Request IDE refresh: `scripts/request_refresh.py`
