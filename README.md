# Code Trace Tree
[![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/saidake/code-trace-tree-jetbrains?sort=semver)](https://github.com/saidake/code-trace-tree-jetbrains/releases/latest)
[![Version](https://img.shields.io/jetbrains/plugin/v/33246.svg)](https://plugins.jetbrains.com/plugin/33246)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33246.svg)](https://plugins.jetbrains.com/plugin/33246)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](./LICENSE)
![Build](https://github.com/saidake/code-trace-tree-jetbrains/actions/workflows/release.yml/badge.svg)

<img src="docs/assets/logo.png" width="100" alt="Code Trace Tree logo">

<p>
  <a href="https://plugins.jetbrains.com/plugin/33246-code-trace-tree">
    <img src="https://img.shields.io/badge/Plugin_home_page-black?style=for-the-badge&logo=jetbrains&logoColor=white" alt="Plugin home page">
  </a>
</p>

----

<!-- Plugin description -->
<p>
  Let AI agents help you build and display code workflows as nested trace points
  (lines, files, and directories) so you can follow the flow and jump back to source anytime.
  <b>Double-click</b> any trace point to navigate to its source, with support for multiple trace levels.
</p>
<p>
  By default, add trace points from the editor right-click menu.
  Install the Agent Skill so a coding agent can search, add, move, and rebind traces, and
  notify the IDE when you ask (for example Claude Code, Cursor, or Gemini CLI).<br/>
  This plugin does <b>not</b> include an AI agent; install your preferred agent separately, then
  install the Code Trace Tree skill. Once installed, the agent can
  <b>auto-load</b> it when relevant.
</p>
<!-- Plugin description end -->

# Preview
![](docs/assets/preview-1-jetbrains.png)
![](docs/assets/preview-2-jetbrains.png)

<!-- Plugin description -->
<h1>How to use</h1>
<ol>
  <li>Open the <b>Code Trace Tree</b> tool window (right side of the IDE).</li>
  <li>Use the <b>Profile</b> selector under the toolbar to switch trees, add a profile (+), or delete one from the dropdown.</li>
  <li>In the editor, right-click a line in a <b>project file</b> and choose:
    <ul>
      <li><b>Create a Root Trace Point</b> — start a new line-level trace tree (selects the new node; does not jump)</li>
      <li><b>Create a Trace Point (Under Selected)</b> — add a child under the selected node(s) in the tree (new node is selected)</li>
      <li><b>Update the selected code trace point</b> — move the selected tree node(s) to the current line</li>
      <li><b>Go to the Trace Point in the tree panel (Only matching)</b> — selects and reveals matching node(s) for the current line; does nothing when none match</li>
    </ul>
  </li>
  <li>In the <b>Project</b> tool window, right-click a file or directory <b>inside the project</b> and choose:
    <ul>
      <li><b>Create a Root Trace Point</b> — add a file or directory node at the root</li>
      <li><b>Create a Trace Point (Under Selected)</b> — add that file/directory under the selected tree node(s)</li>
    </ul>
  </li>
  <li>Single-click a node to select it; double-click to jump to that location (line, file, or Project View for directories).</li>
  <li>Right-click a node and choose <b>Go to Trace Point</b> (first item) to jump to that location (same as double-click).</li>
  <li>Right-click a node and choose <b>Copy Label</b> (or use Ctrl/Cmd+C) to copy its display text, e.g. <code>test233 (TestControllerWebFlux.java:54)</code>.</li>
  <li>Right-click a line trace point and choose <b>Show Line Content</b> to view its saved trimmed line text.</li>
  <li>Use the tool window toolbar to expand/collapse, <b>Recheck Trace Availability</b>, <b>Remove Invalid Trace Points</b>, reorder, highlight, prompt for name on create, or edit descriptions. Import/Export live under <b>Advanced Settings</b>.</li>
</ol>
<p>
  <b>TIPS:</b> Prefer creating line trace points on text that is <b>unique in that file</b> (or uncommon),
  not generic lines like <code>}</code> or <code>return;</code>. Empty lines are not allowed.
  The plugin stores occurrence counts to re-find the line after it moves; unique content rebinds more reliably.
  Opening a file re-checks line traces in that file so highlights and validity stay aligned.
  Use <b>Recheck Trace Availability</b> in the toolbar to recheck every line, file, and directory trace.
</p>

<h1>Agent Skill</h1>
<p>
  This plugin does <b>not</b> ship an AI agent. Install your preferred coding agent, then install
  the Code Trace Tree skill. Once installed, the agent can <b>auto-load</b> it when your
  request is relevant. The skill is general — any agent that can load skill folders can use it.
</p>
<p>Example agents:</p>
<ul>
  <li><a href="https://claude.com/claude-code">Claude Code</a></li>
  <li><a href="https://cursor.com">Cursor</a></li>
  <li><a href="https://docs.github.com/en/copilot">GitHub Copilot</a> (agent skills)</li>
  <li><a href="https://developers.openai.com/codex">Codex</a></li>
  <li><a href="https://geminicli.com">Gemini CLI</a></li>
</ul>
<p>The skill lets the agent:</p>
<ul>
  <li>Resolve the bound global storage XML for the project</li>
  <li>Search, add, move, and delete trace points</li>
  <li>Rebind line locations after source edits on disk</li>
  <li>Ask the IDE to reload / refresh plugin data</li>
  <li>Select or navigate to nodes in the Code Trace Tree tool window</li>
</ul>
<p>
  <b>Python required:</b> the main skill ops (<code>trace_tree</code> search / add / move / delete / rebind)
  run <code>trace_tree.py</code>, so <b>Python 3</b> must be on your <code>PATH</code>
  (<code>python3</code> or <code>python</code>).
  Resolve / refresh / select helper scripts are plain shell or batch and do not need Python.
</p>

<h2>Agent Skill Installation</h2>
<h3>Option 1: Install with npx (Recommended)</h3>
<p>
  Requires <a href="https://nodejs.org/">Node.js</a> (<code>npx</code>).
  Pass <code>-g</code> for all projects; omit it for this project only.
  On Windows, add <code>--copy</code> if symlinks are not available.
  This installs from the dedicated skill repo (small clone, default branch).
  If the skill is already installed, remove it first —
  <code>npx skills add</code> does not overwrite an existing skill.
</p>
<pre><code>npx skills remove code-trace-tree -g
npx skills add saidake/code-trace-tree-skill -g</code></pre>
<p>Explicit, non-interactive install for the agent you currently use (Cursor example):</p>
<pre><code>npx -y skills remove code-trace-tree --agent cursor --global --yes
npx -y skills add saidake/code-trace-tree-skill --skill code-trace-tree --agent cursor --global --copy --yes</code></pre>
<p>
  Set <code>--agent</code> to your current agent, for example
  <code>cursor</code>, <code>claude-code</code>, <code>github-copilot</code>,
  <code>codex</code>, or <code>gemini-cli</code>.
</p>
<p>Try without installing (Codex example; same <code>--agent</code> values):</p>
<pre><code>npx skills use saidake/code-trace-tree-skill@code-trace-tree --agent codex</code></pre>

<h3>Option 2: Install from a ZIP file</h3>
<ol>
  <li>Download <code>code-trace-tree-skill-1.3.3.zip</code> from the <a href="https://github.com/saidake/code-trace-tree-jetbrains/releases/tag/v1.3.3">GitHub Release</a> (one zip works across agents).</li>
  <li>Delete any existing <code>code-trace-tree</code> folder in the skills directory for your agent (table below), then extract the zip <b>into</b> that directory. The zip contains one <code>code-trace-tree</code> folder (with <code>SKILL.md</code> inside). Global = all projects; project-local = this repo only.</li>
</ol>
<p>
  Done when the skills directory contains <code>code-trace-tree/SKILL.md</code>
  (not the zip file, and not an extra nested <code>code-trace-tree/code-trace-tree</code> folder).
</p>
<table>
  <thead>
    <tr><th>Agent (examples)</th><th>Global</th><th>Project-local</th></tr>
  </thead>
  <tbody>
    <tr><td>Claude Code</td><td><code>~/.claude/skills/</code></td><td><code>.claude/skills/</code></td></tr>
    <tr><td>Cursor</td><td><code>~/.cursor/skills/</code></td><td><code>.cursor/skills/</code></td></tr>
    <tr><td>GitHub Copilot</td><td><code>~/.copilot/skills/</code></td><td><code>.github/skills/</code></td></tr>
    <tr><td>Codex</td><td><code>~/.agents/skills/</code></td><td><code>.agents/skills/</code></td></tr>
    <tr><td>Gemini CLI</td><td><code>~/.gemini/skills/</code></td><td><code>.gemini/skills/</code></td></tr>
  </tbody>
</table>

<h2>How to use the skill</h2>
<p>
  Installing the skill makes it available to the agent:
</p>
<ul>
  <li><b>Project-local</b> — <code>npx...</code>, omit <code>-g</code>, or extract the zip into the project skills folder</li>
  <li><b>Global</b> — <code>npx...</code>, pass <code>-g</code>, or extract the zip into the global skills folder</li>
</ul>
<p>
  If the IDE is open on this project, it loads the agent’s
  trace point changes in real time (no manual reload).
</p>
<p>Examples:</p>
<pre><code>Help me generate some simple trace points related to the current topic.
</code></pre>
<pre><code>Add simple trace points along the call path of method `test`.
</code></pre>

<h1>Storage</h1>
<p>Trace data is stored in a shared global folder:</p>
<ul>
  <li>Windows: <code>%LOCALAPPDATA%\code-trace-tree</code></li>
  <li>macOS: <code>~/Library/Application Support/code-trace-tree</code></li>
  <li>Linux: <code>$XDG_CONFIG_HOME/code-trace-tree</code> or <code>~/.config/code-trace-tree</code></li>
</ul>
<p>
  Each project uses <code>&lt;projectId&gt;.xml</code> in that folder
  (legacy <code>&lt;FolderName&gt;.xml</code> files from older releases are still resolved and
  renamed when found). The IDE may cache the id in
  <code>.idea/code-trace-tree.project.id</code>. Agents use path mode: prefer an existing
  <code>.idea</code> id (recreate that XML if missing), else path match, else Case C create
  with <code>&lt;path&gt;</code> only (never write the <code>.idea</code> id). The IDE binds
  via path match + <code>storage-ready</code> (signal body = project path).
  Highlight colors are a global preference (<code>settings.xml</code> in that folder),
  shared across projects and IDEs (defaults <code>#FFFFC8</code> light, <code>#236C60</code> dark).
</p>
<!-- Plugin description end -->

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE
- Shared agent skill: `https://github.com/saidake/code-trace-tree-skill` (copy in `skills/code-trace-tree/` for zip packaging)

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
