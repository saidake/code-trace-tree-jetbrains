# Contributing

Welcome! This short guide explains how to contribute effectively to **Code Trace Tree**.

## Submitting an issue

- If you find a bug, please submit an issue to our GitHub [repository](https://github.com/saidake/code-trace-tree-jetbrains/issues).
- Before submitting, search the issue tracker to see if your problem already exists. Existing issues may already have workarounds or ongoing fixes.
- Include the plugin version, IDE version (e.g. IntelliJ IDEA 2025.1), OS, and enough detail to reproduce the problem (steps, screenshots, and logs help a lot).

## Branch Naming Convention

Use lowercase, kebab-case, and a type prefix:

- `feature/<short-title>`
- `bugfix/<short-title>`
- `docs/<short-title>`

**Example**: `bugfix/fix-trace-point-highlight`

For release preparation branches:

- `release/<version>`

**Example**: `release/1.0.1`

## Commits

- Keep commits small and focused.
- This makes it easier for reviewers to understand and track changes.
- Prefer this repository's commit style:

```text
[Component] type: Short summary
```

**Examples**:

- `[Global] feat: Reorder editor menu and add go-to-tree action`
- `[TreeView] fix: Selection is lost after renaming a trace point`
- `[README] docs: Add Preview section and update How to use`

Common types: `feat`, `fix`, `docs`/`doc`, `style`, `refactor`, `chore`, `ci`.

See [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) for additional inspiration.

## Development Setup

The IntelliJ Platform plugin module lives under `main/`.

### Prerequisites

- [JDK 21](https://adoptium.net/)
- IntelliJ IDEA (for running/debugging the plugin via **Run Plugin**)
- Git

### Build

```bash
./gradlew :main:buildPlugin
```

On Windows (PowerShell):

```powershell
.\gradlew.bat :main:buildPlugin
```

### Run in a sandbox IDE

1. Open the project root in IntelliJ IDEA and import as a Gradle project.
2. Use the **Run Plugin** configuration, or run:

```bash
./gradlew :main:runIde
```

### Project layout

| Path | Purpose |
| - | - |
| `main/` | Plugin module (`build.gradle.kts`, sources, resources) |
| `main/src/main/kotlin/` | Kotlin sources (`com.pidifa.codetracetree`) |
| `main/src/main/resources/` | `plugin.xml`, icons, and other resources |
| `docs/` | Documentation assets (logo, preview image) |
| `.run/` | Shared IDE run configuration (**Run Plugin**) |
| `.github/workflows/` | CI / release workflows |

### Checks before opening a PR

```bash
./gradlew :main:compileKotlin :main:buildPlugin
```

Manually smoke-test the flows you touched (create/update/go-to trace points, tree navigation, import/export) in the sandbox IDE when possible.

## Pull Requests

Use the following procedure to submit a pull request:

1. Fork Code Trace Tree on GitHub (_[How to fork a repo?](https://docs.github.com/en/github/getting-started-with-github/fork-a-repo)_)

2. Create a branch from `main` (see [Branch Naming](#branch-naming-convention))

```bash
git checkout -b bugfix/<short-title>
```

3. Make the changes and push to your branch (see [Commits](#commits))

```bash
git push origin bugfix/<short-title>
```

4. Initiate a pull request on GitHub (_[How to create a PR?](https://docs.github.com/en/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request)_)

Try to provide as much description behind the context of your changes and how to verify them. Screenshots and videos are always welcome ^_^

5. Ensure the project builds cleanly (`./gradlew :main:buildPlugin`) and that any relevant smoke tests pass.

Done :)

By following these conventions, you help us keep Code Trace Tree stable, reliable, and easy to maintain. Thank you for contributing!
