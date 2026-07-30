# Code Trace Tree
![GitHub release (latest SemVer)](https://img.shields.io/github/v/release/saidake/code-trace-tree-jetbrains?sort=semver)
![Build](https://github.com/saidake/code-trace-tree-jetbrains/actions/workflows/release.yml/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<img src="docs/assets/logo.png" width="100" alt="Code Trace Tree logo">

----

<!-- Plugin description -->
Code Trace Tree is a JetBrains plugin that lets you trace code in a tree structure.
Double click any trace point to navigate to its source, with support for multiple trace levels.
<!-- Plugin description end -->

# Preview
![](./docs/assets/preview.png)

# How to use

1. Open the **Code Trace Tree** tool window (right side of the IDE).
2. In the editor, right-click a line and choose:
   - **Create a Root Trace Point** — start a new trace tree
   - **Create a Trace Point (Under Selected)** — add a child under the selected node(s) in the tree
   - **Update the selected code trace point** — move the selected tree node(s) to the current line
   - **Go to the Trace Point in the tree panel** — shown only when the current single line is a highlighted trace point; selects and reveals that node in the tree
3. Double-click a node in the tree to jump to that location in the source.
4. Use the tool window toolbar to expand/collapse, reorder, highlight, import/export, or edit descriptions.

# Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE

# Contributing

If you would like to contribute to the code base or fix an issue, please see [CONTRIBUTING.md](CONTRIBUTING.md).
