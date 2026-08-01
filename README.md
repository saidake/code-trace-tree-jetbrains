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
  Pair it with the Claude Skill so AI agents can search, add, move, and rebind traces, refresh the IDE,
  and—when <b>Claude Assist</b> is enabled—auto-sync topic-related workflow points as you discuss code.
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
  <code>.idea/code-trace-tree.refresh-request</code> (or by saving the XML while the project is open).
  To select nodes in the tool window (and navigate when exactly one id is listed), write one
  node UUID per line to <code>.idea/code-trace-tree.select-request</code>.
  See <code>.claude/skills/code-trace-tree/</code> for the Claude Code skill and helper scripts.
</p>

<h1>Claude Skill</h1>
<p>The Claude Skill lets Claude Code:</p>
<ul>
  <li>Resolve the bound global storage XML for the project</li>
  <li>Search, add, move, and delete trace points</li>
  <li>Rebind line locations after source edits on disk</li>
  <li>Ask the IDE to reload / refresh plugin data</li>
  <li>Select or navigate to nodes in the Code Trace Tree tool window</li>
  <li>Auto-sync topic-related traces when <b>Claude Assist</b> is enabled</li>
</ul>

<h2>Install Claude Skill (Linux &amp; macOS)</h2>
<p>
  Download the Code Trace Tree Claude Skill <code>zip</code> file, and extract it into your Claude skills directory.
</p>
<p>For a global installation:</p>
<pre><code>curl -L https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.2/code-trace-tree-skill-1.1.2.zip -o code-trace-tree-skill-1.1.2.zip
unzip code-trace-tree-skill-1.1.2.zip -d ~/.claude/skills/
rm code-trace-tree-skill-1.1.2.zip
</code></pre>
<p>For a project-level installation:</p>
<pre><code>curl -L https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.2/code-trace-tree-skill-1.1.2.zip -o code-trace-tree-skill-1.1.2.zip
mkdir -p .claude/skills
unzip code-trace-tree-skill-1.1.2.zip -d .claude/skills/
rm code-trace-tree-skill-1.1.2.zip
</code></pre>

<h2>Install Claude Skill (Windows)</h2>
<p>
  Download the Code Trace Tree Claude Skill <code>zip</code> file, and extract it into your Claude skills directory.
</p>
<p>For a global installation using PowerShell:</p>
<pre><code>Invoke-WebRequest -Uri "https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.2/code-trace-tree-skill-1.1.2.zip" -OutFile "code-trace-tree-skill-1.1.2.zip"
Expand-Archive -Path "code-trace-tree-skill-1.1.2.zip" -DestinationPath "$HOME\.claude\skills"
Remove-Item "code-trace-tree-skill-1.1.2.zip"
</code></pre>
<p>For a project-level installation using PowerShell:</p>
<pre><code>Invoke-WebRequest -Uri "https://github.com/saidake/code-trace-tree-jetbrains/releases/download/v1.1.2/code-trace-tree-skill-1.1.2.zip" -OutFile "code-trace-tree-skill-1.1.2.zip"
New-Item -ItemType Directory -Force -Path ".claude\skills"
Expand-Archive -Path "code-trace-tree-skill-1.1.2.zip" -DestinationPath ".claude\skills"
Remove-Item "code-trace-tree-skill-1.1.2.zip"
</code></pre>
<!-- Plugin description end -->

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
