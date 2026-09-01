## v1.3.6

- Bundle the Agent Skill in the plugin; toolbar **Agent Skill** installs or updates it per coding agent
- Prompt once per bundled skill version when a detected agent is missing or outdated

## v1.3.5

- Clarify Agent Skill install: Python 3 on PATH; ZIP first; `npx` only if Node.js is present

## v1.3.3

- Agent skill `create_tree.py` generates a nested workflow in one call (ensures existing nodes, adds new ones)
- LINE locators require `--file`, `--line`, and `--content`; the script computes occurrence
- Mutating skill ops auto-create missing storage
- Remove the skill before `npx skills add` (add does not overwrite)

## v1.3.2

- Toolbar Advanced Settings uses a gear icon
- Tree context menu **Go to Trace Point** is first (navigates the clicked node)
- Install the Agent Skill from the dedicated repo: `npx skills add saidake/code-trace-tree-skill`
- Add a second Marketplace preview screenshot

## v1.3.1

- Highlight line colors are a global preference (`settings.xml`), shared across projects and IDEs; dark default is `#236C60`
- First Advanced Settings save creates `settings.xml` and migrates leftover project colors; peers reload via `request_refresh_global_settings`

## v1.3.0

- Split skill `add` vs `ensure`; slim skill docs to script ops
- Disable trace highlights and editor context menus in Git / IDE diff panes
- Skill: do not delete existing traces unless the user asks
- Clarify Agent Skill install: extract the zip into the agent skills directory (replace `code-trace-tree` if present)
- Update Marketplace preview screenshot

## v1.2.10

- Expand the drop-target parent after drag-and-drop reparent so the moved child stays visible

## v1.2.9

- **Recheck Trace Availability**: reload bound XML and validate all traces; tiered peer refresh (full / profile / settings)
- Rebind LINE traces on file open; content-rebind after bulk external edits
- Toolbar: **Remove Invalid Trace Points**; Import/Export moved into Advanced Settings; context menu **Copy Label**
- Preserve tree selection across self profile-refresh echoes; scope disk watching to open LINE buffers and path tips

## v1.2.8

- Update Marketplace plugin icons (light/dark)
- Add Plugin home page button linking to JetBrains Marketplace
- Document the Agent Skill as agent-agnostic (listed agents are examples)
- Preferred workflow format: nest by call flow (sibling fan-out under a call)
- Clarify skill auto-load (project/global) and real-time IDE sync in README
- Align version with VS Code / Cursor companions

## v1.2.6

- Align version with VS Code / Cursor companions (Trace Points webview list on those IDEs)

## v1.2.5

- Editor and Project View Code Trace Tree actions only for files under the project root (relative `tracePath`)
- After create, select the new node in the tree without jumping to source
- Agent path-mode storage: `storage-ready` carries project path; reuse existing `.idea` id; recreate missing XML with the same id

## v1.2.4

- Always show **Go to the Trace Point in the tree panel (Only matching)** in the editor context menu; no-ops when nothing matches

## v1.2.3

- Advanced Settings: persist highlight line background colors (light/dark) in shared project XML
- Toolbar Advanced Settings action; editor highlights use the configured theme colors
- Maximize Description toolbar toggle; resizable description/tree splitter
- Expand Marketplace intro for building and displaying code workflows

## v1.2.1

- Idea-only project id (`.idea/code-trace-tree.project.id`); Case B path bind reuses the latest matching XML or copy-on-writes a new UUID when several match
- Case C lazy create overwrites a stale idea id with a new UUID XML
- Simplify Marketplace intro copy

## v1.2.0

- Bind Case C (unbound) windows via global `<projectId>.storage-ready` when agents create storage
- Poll agent signal files so rapid refreshes are not missed on Windows
- Agent-driven reloads bypass the self-write ignore window

## v1.1.12

- Align README Agent Skill install links and zip names with v1.1.12

## v1.1.11

- Align version with the VS Code companion (jump from 1.1.8; no separate JetBrains 1.1.9 / 1.1.10 builds)
- Lazy project storage (Case C): create storage on first real use
- Agent signals: `request_refresh` and `request_refresh_profile` (no XML file watch)
- Tree context menu **Show Line Content** for LINE nodes (copyable)
- Block creating or updating LINE traces on empty lines
- Update README preview and badges

## v1.1.8

- Agents edit traces only when asked via the skill (no auto-sync toolbar toggle)
- Document how to prompt the `code-trace-tree` skill in the README

## v1.1.7

- Reset the description area when switching profiles
- Fix empty Code Trace Tree tool window ("Nothing to show") caused by refreshing description before the tree was initialized

## v1.1.6

- Agent Skill: do not refuse OS Config Dir writes as outside-workspace
- Agent Skill: add repeatable `--parent-id`; disambiguate duplicate LINE tips by occurrence
- Agent Skill: annotated multi-profile XML example

## v1.1.5

- Store project data as `<projectId>.xml`; still resolve and rename legacy `<FolderName>.xml`
- Move agent refresh/select signals to global `signals/<projectId>.*` with a 60s TTL (multi-window safe)
- Ship one shared Agent Skill zip (`code-trace-tree-skill-X.Y.Z.zip`); clarify Agent Skill Path and OS Config Dir in the skill
- Forgiving LINE locators, idempotent add, and absolute skill script paths

## v1.1.4

- Lower IDE compatibility floor to IntelliJ Platform 2024.1 (`sinceBuild` 241)

## v1.1.3

- Initialize project id and default `main` profile as soon as a project opens
- Clarify Agent Skill install commands in the Marketplace description

## v1.1.2

- Add agent select-request signal to select/reveal nodes and navigate when exactly one id is listed
- Add `trace_tree` skill scripts for search/add/move/delete/rebind (no occurrence args from the agent)
- Rebind LINE locations after disk edits (skill + IDE VFS content rebind)
- Replace skill Python helpers with shell/batch scripts; expand Agent Skill docs and install instructions

## v1.1.1

- Color trace point names in the tree and add a space before the location suffix
- Copy a node's display text from the context menu or with Ctrl/Cmd+C
- Add a toolbar toggle to skip the name prompt when creating trace points
- Remove the optional description dialog when creating file or directory traces
- Update Marketplace plugin icons

## v1.1.0

- Update Marketplace plugin icons

## v1.0.9

- Set Marketplace plugin icons to 40x40
- Format storage folder paths as a list in the plugin description

## v1.0.8

- Update Marketplace plugin logo
- Attach Agent Skill ZIP to GitHub Releases for easier agent install

## v1.0.7

- Add file and directory trace points from the Project View
- Introduce `traceType` (`LINE` / `FILE` / `DIRECTORY`) with `traceName`, `baseName`, and `tracePath`
- Support descriptions for all trace types; remove legacy config migration

## v1.0.6

- Reload plugin data when the global storage XML changes or `.idea/code-trace-tree.refresh-request` is written
- Add Agent Skill and scripts so agents can resolve storage, edit traces, and notify IDEA to refresh

## v1.0.5

- Store trace data in OS global config (`%LOCALAPPDATA%` / Application Support / XDG) with a project id file under `.idea`
- Share-friendly storage and export XML (no per-node `projectPath` / `isValid`); single export uses `<traceProfile>`
- Document storage location and manual cleanup in the plugin description

## v1.0.4

- Include How to use instructions in the Marketplace plugin description

## v1.0.3

- Update plugin logos for Marketplace and Plugin Manager

## v1.0.2

- Add Marketplace / Plugin Manager logos (`pluginIcon.svg`)

## v1.0.1

- Add Trace Profiles so you can keep multiple independent trace trees (default: `main`)
- Add, switch, and delete profiles from the tool window
- Export the current profile or all profiles; import with explicit replace / new / merge choices

## v1.0.0

- Initial Marketplace release
