# Code Trace Tree
![Build](https://github.com/saidake/code-trace-tree-jetbrains/actions/workflows/release.yml/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<img src="docs/assets/logo.png" width="100" alt="Code Trace Tree logo">

----

<!-- Plugin description -->
Code Trace Tree is a JetBrains plugin that lets you trace code in a tree structure.
Double click any trace point to navigate to its source, with support for multiple trace levels.
<!-- Plugin description end -->

----

## How to use

1. Open the **Code Trace Tree** tool window (right side of the IDE).
2. In the editor, right-click a line and choose:
   - **Create a Root Trace Point** — start a new trace tree
   - **Create a Trace Point (Under Selected)** — add a child under the selected node(s)
   - **Update the selected code trace point** — move selected nodes to the current line
3. Double-click a node in the tree to jump to that location in the source.
4. Use the tool window toolbar to expand/collapse, reorder, highlight, import/export, or edit descriptions.

### Screenshots

> Replace these placeholders with real screenshots when available.

![Tool window overview](docs/assets/screenshot-tool-window.png)

![Create trace point from editor](docs/assets/screenshot-create-trace-point.png)

![Navigate by double-click](docs/assets/screenshot-navigate.png)

## Development

- JDK 21
- Open the project root in IntelliJ IDEA and import as a Gradle project
- Run the **Run Plugin** configuration (or `:main:runIde`) to launch a sandbox IDE

## Release

Push a version tag to trigger build and Marketplace publish:

```bash
git tag v1.0.0
git push origin v1.0.0
```
