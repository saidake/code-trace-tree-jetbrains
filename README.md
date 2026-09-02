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
  Trace code in a tree structure with AI support. Build and display code workflows as nested trace points
  (lines, files, and directories) so you can follow the flow and jump back to source anytime.
  <b>Double-click</b> any trace point to navigate to its source, with support for multiple trace levels.
</p>
<p>
  Use it manually or pair it with the Agent Skill so coding agents such as Claude Code, Cursor, or Gemini CLI
  can search, add, move, and rebind traces, and notify the IDE to refresh.<br/>
  This plugin does <b>not</b> include an AI agent; install your preferred agent separately, then
  install the <code>code-trace-tree</code> skill. Once installed, the agent can
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
  <li>Use the tool window toolbar to expand/collapse, <b>Recheck Trace Availability</b>, <b>Remove Invalid Trace Points</b>, reorder, highlight, prompt for name on create, or edit descriptions. <b>Agent Skill</b> installs or updates the bundled skill for coding agents. Import/Export live under <b>Advanced Settings</b>.</li>
</ol>
<p>
  <b>TIPS:</b> Prefer creating line trace points on text that is <b>unique in that file</b> (or uncommon),
  not generic lines like <code>}</code> or <code>return;</code>. Empty lines are not allowed.
  The plugin stores occurrence counts to re-find the line after it moves; unique content rebinds more reliably.
</p>

<h1>Agent Skill</h1>
<p>
  This plugin does <b>not</b> ship an AI agent. Install your preferred coding agent, then install
  the <code>code-trace-tree</code> skill. Once installed, the agent can <b>auto-load</b> it when your
  request is relevant, and the IDE syncs the agent's trace-point changes as they are written.
  The skill is general — any agent that can load skill folders can use it.
</p>
<p>Same agents as <a href="https://github.com/vercel-labs/skills">npx skills</a> (global install). Examples:</p>
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

<h2>Agent Skill Installation</h2>
<p>
  <b>Python required to run skill ops:</b> skill scripts need <b>Python 3</b> on your
  <code>PATH</code> (<code>python3</code> or <code>python</code>). Copying the skill from the IDE does not require Python.
</p>
<p>
  Open the <b>Code Trace Tree</b> tool window and click <b>Agent Skill</b> on the toolbar
  (download icon):
</p>
<p>
  <img src="docs/assets/jetbrains-install-skill-1.png" alt="Click Agent Skill on the Code Trace Tree toolbar">
</p>
<ul>
  <li>The status page shows the bundled skill version, whether <b>Python 3</b> is on
    <code>PATH</code>, and which agents already have the skill.</li>
  <li><b>Choose agents to install</b> — pick agents that do not have it yet.</li>
  <li><b>Install / Update</b> — copy the bundled <code>code-trace-tree</code> skill into
    each listed agent's global skills folder
    (for example <code>~/.cursor/skills/code-trace-tree</code>).</li>
  <li><b>Remove from installed agents</b> — delete those copies.</li>
</ul>
<p>
  <img src="docs/assets/jetbrains-install-skill-2.png" alt="Agent Skill status page: choose agents, install or update, or remove">
</p>
<p>
  On first project open, if a detected agent is missing the skill or is behind the bundled
  version, the plugin shows a notification (once per skill version; <b>Dismiss</b> or
  opening the status page is remembered in global <code>settings.xml</code>).
</p>
<p>
  ZIP and <code>npx skills</code> (including a project-local copy) remain on
  <a href="https://github.com/saidake/code-trace-tree-skill">code-trace-tree-skill</a>
  if you need them.
</p>

<h2>How to use the skill</h2>
<p>Once the skill is installed, use it in your agent chat:</p>
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
<!-- Plugin description end -->

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE
- Shared agent skill: `https://github.com/saidake/code-trace-tree-skill` (copy in `skills/code-trace-tree/` for zip packaging)

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
