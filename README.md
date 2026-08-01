# Code Trace Tree
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/saidake/code-trace-tree-jetbrains?sort=semver)
![Build](https://github.com/saidake/code-trace-tree-jetbrains/actions/workflows/release.yml/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/33246.svg)](https://plugins.jetbrains.com/plugin/33246)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/33246.svg)](https://plugins.jetbrains.com/plugin/33246)

<img src="docs/assets/logo.png" width="100" alt="Code Trace Tree logo">

----

<!-- Plugin description -->
<p>
  Code Trace Tree is a JetBrains plugin that lets you trace code in a tree structure.
  Double-click any trace point to navigate to its source, with support for multiple trace levels.
</p>
<p>
  Pair it with the Agent Skill so Claude Code, Cursor, GitHub Copilot, Codex, or Gemini CLI can search,
  add, move, and rebind traces, refresh the IDE, and—when <b>Agent Notes</b> is enabled—auto-sync
  topic-related workflow points as you discuss code.
  This plugin does <b>not</b> include an AI agent; install your preferred agent separately first, then
  add the Code Trace Tree skill.
</p>
<!-- Plugin description end -->

# Preview
![](docs/assets/preview.png)

<!-- Plugin description -->
<h1>How to use</h1>
<ol>
  <li>Open the <b>Code Trace Tree</b> tool window (right side of the IDE).</li>
  <li>Use the <b>Profile</b> selector under the toolbar to switch trees, add a profile (+), or delete one from the dropdown.</li>
  <li>In the editor, right-click a line and choose:
    <ul>
      <li><b>Create a Root Trace Point</b> — start a new line-level trace tree</li>
      <li><b>Create a Trace Point (Under Selected)</b> — add a child under the selected node(s) in the tree</li>
      <li><b>Update the selected code trace point</b> — move the selected tree node(s) to the current line</li>
      <li><b>Go to the Trace Point in the tree panel</b> — shown only when the current single line is a highlighted trace point; selects and reveals that node in the tree</li>
    </ul>
  </li>
  <li>In the <b>Project</b> tool window, right-click a file or directory and choose:
    <ul>
      <li><b>Create a Root Trace Point</b> — add a file or directory node at the root</li>
      <li><b>Create a Trace Point (Under Selected)</b> — add that file/directory under the selected tree node(s)</li>
    </ul>
  </li>
  <li>Double-click a node in the tree to jump to that location (line, file, or Project View for directories).</li>
  <li>Right-click a node and choose <b>Copy</b> (or use Ctrl/Cmd+C) to copy its display text, e.g. <code>test233 (TestControllerWebFlux.java:54)</code>.</li>
  <li>Use the tool window toolbar to expand/collapse, reorder, highlight, prompt for name on create, import/export, or edit descriptions.</li>
</ol>
<p>
  <b>TIPS:</b> Prefer creating line trace points on text that is <b>unique in that file</b> (or uncommon),
  not generic lines like <code>}</code> or <code>return;</code>.
  The plugin stores occurrence counts to re-find the line after it moves; unique content rebinds more reliably.
</p>

<h1>Agent Skill</h1>
<p>
  This plugin does <b>not</b> ship an AI agent. Install one of the supported agents first, then add the
  Code Trace Tree skill so the agent can talk to the plugin.
</p>
<p>Supported agents:</p>
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
  <li>Auto-sync topic-related traces when <b>Agent Notes</b> is enabled in the IDE</li>
</ul>
<p>
  <b>Python required:</b> the main skill ops (<code>trace_tree</code> search / add / move / delete / rebind)
  run <code>trace_tree.py</code>, so <b>Python 3</b> must be on your <code>PATH</code>
  (<code>python3</code> or <code>python</code>).
  Resolve / refresh / select helper scripts are plain shell or batch and do not need Python.
</p>
<p>
  Shared skill source in this repo: <code>skills/code-trace-tree/</code>
  (same package for every agent; only the extract path differs).
  Releases attach <code>code-trace-tree-skill-&lt;version&gt;.zip</code>.
</p>

<h2>Install skill — extract locations</h2>
<p>
  Download <code>code-trace-tree-skill-1.1.4.zip</code> from the GitHub Release
  (one zip for all agents).
  Remove any existing <code>code-trace-tree</code> skill folder first, then extract into the
  skills directory for your agent:
</p>
<table>
  <thead>
    <tr><th>Agent</th><th>Global</th><th>Project-local</th></tr>
  </thead>
  <tbody>
    <tr><td>Claude Code</td><td><code>~/.claude/skills/</code></td><td><code>.claude/skills/</code></td></tr>
    <tr><td>Cursor</td><td><code>~/.cursor/skills/</code></td><td><code>.cursor/skills/</code></td></tr>
    <tr><td>GitHub Copilot</td><td><code>~/.copilot/skills/</code></td><td><code>.github/skills/</code></td></tr>
    <tr><td>Codex</td><td><code>~/.agents/skills/</code></td><td><code>.agents/skills/</code></td></tr>
    <tr><td>Gemini CLI</td><td><code>~/.gemini/skills/</code></td><td><code>.gemini/skills/</code></td></tr>
  </tbody>
</table>

<h2>Install example (Claude Code, Linux &amp; macOS)</h2>
<pre><code>curl -L https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.4/code-trace-tree-skill-1.1.4.zip -o code-trace-tree-skill-1.1.4.zip</code>
<code>rm -rf ~/.claude/skills/code-trace-tree</code>
<code>mkdir -p ~/.claude/skills</code>
<code>unzip code-trace-tree-skill-1.1.4.zip -d ~/.claude/skills/</code>
<code>rm code-trace-tree-skill-1.1.4.zip</code>
</pre>
<p>Project-local: extract into <code>.claude/skills/</code> instead of <code>~/.claude/skills/</code>. For other agents, use the same zip and extract into that agent’s folder from the table above.</p>

<h2>Install example (Claude Code, Windows PowerShell)</h2>
<pre><code>Invoke-WebRequest -Uri "https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.4/code-trace-tree-skill-1.1.4.zip" -OutFile "code-trace-tree-skill-1.1.4.zip"</code>
<code>Remove-Item -Recurse -Force "$HOME\.claude\skills\code-trace-tree" -ErrorAction SilentlyContinue</code>
<code>New-Item -ItemType Directory -Force -Path "$HOME\.claude\skills" | Out-Null</code>
<code>Expand-Archive -Path "code-trace-tree-skill-1.1.4.zip" -DestinationPath "$HOME\.claude\skills" -Force</code>
<code>Remove-Item "code-trace-tree-skill-1.1.4.zip"</code>
</pre>
<p>Project-local: extract into <code>.claude\skills\</code>. For Cursor / Copilot / Codex / Gemini, use the same zip and change the destination path using the table above.</p>

<h1>Storage</h1>
<p>Trace data is stored in a shared global folder:</p>
<ul>
  <li>Windows: <code>%LOCALAPPDATA%\code-trace-tree</code></li>
  <li>macOS: <code>~/Library/Application Support/code-trace-tree</code></li>
  <li>Linux: <code>$XDG_CONFIG_HOME/code-trace-tree</code> or <code>~/.config/code-trace-tree</code></li>
</ul>
<p>
  Each project keeps only a small id file under <code>.idea/code-trace-tree.project.id</code>
  (falls back to <code>.vscode/code-trace-tree.project.id</code> when present).
  Old unused XML files are not deleted automatically — remove them from that folder if you no longer need them.
</p>
<p>
  External agents can edit the global XML and ask the IDE to reload by writing
  <code>signals/&lt;projectId&gt;.request_refresh</code> under the global storage folder
  (or by saving the XML while the project is open).
  To select nodes in the tool window (and navigate when exactly one id is listed), write one
  node UUID per line to <code>signals/&lt;projectId&gt;.select_trace_points</code>.
  Signal files expire after 60 seconds. All open IDE windows for that project watch the same
  signals folder. See <code>skills/code-trace-tree/</code> for the shared agent skill and helper scripts.
</p>
<!-- Plugin description end -->

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE
- Shared agent skill source: `skills/code-trace-tree/`

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
