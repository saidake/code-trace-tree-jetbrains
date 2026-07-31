# Code Trace Tree
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/saidake/code-trace-tree-jetbrains?sort=semver)
![Build](https://github.com/saidake/code-trace-tree-jetbrains/actions/workflows/release.yml/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<img src="docs/assets/logo.png" width="100" alt="Code Trace Tree logo">

----

<!-- Plugin description -->
<p>
  Code Trace Tree is a JetBrains plugin that lets you trace code in a tree structure.
  Double click any trace point to navigate to its source, with support for multiple trace levels.
</p>
<!-- Plugin description end -->

# Preview
![](docs/assets/preview.png)

<!-- Plugin description -->
<h3>How to use</h3>
<ol>
  <li>Open the <b>Code Trace Tree</b> tool window (right side of the IDE).</li>
  <li>Use the <b>Profile</b> selector under the toolbar to switch trees, add a profile (+), or delete one from the dropdown.</li>
  <li>In the editor, right-click a line and choose:
    <ul>
      <li><b>Create a Root Trace Point</b> — start a new trace tree</li>
      <li><b>Create a Trace Point (Under Selected)</b> — add a child under the selected node(s) in the tree</li>
      <li><b>Update the selected code trace point</b> — move the selected tree node(s) to the current line</li>
      <li><b>Go to the Trace Point in the tree panel</b> — shown only when the current single line is a highlighted trace point; selects and reveals that node in the tree</li>
    </ul>
  </li>
  <li>Double-click a node in the tree to jump to that location in the source.</li>
  <li>Use the tool window toolbar to expand/collapse, reorder, highlight, import/export, or edit descriptions.</li>
</ol>

<h3>Storage</h3>
<p>
  Trace data is stored in a shared global folder (Windows: <code>%LOCALAPPDATA%\code-trace-tree</code>;
  macOS: <code>~/Library/Application Support/code-trace-tree</code>;
  Linux: <code>$XDG_CONFIG_HOME/code-trace-tree</code> or <code>~/.config/code-trace-tree</code>).
  Each project keeps only a small id file under <code>.idea/code-trace-tree.project.id</code>
  (falls back to <code>.vscode/code-trace-tree.project.id</code> when present).
  Old unused XML files are not deleted automatically — remove them from that folder if you no longer need them.
</p>
<!-- Plugin description end -->

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
