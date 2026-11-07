<p>
  <a href="https://plugins.jetbrains.com/plugin/33246-code-trace-tree">
    <img src="https://img.shields.io/badge/Plugin_home_page-black?style=for-the-badge&logo=jetbrains&logoColor=white" alt="Plugin home page">
  </a>
</p>

# What's New

* **Empty trace point tips**: Improved the empty-state hint to guide you in creating your first trace point.

---

# Install the Plugin with `zip` file

1. Download the release `.zip` file from JetBrains releases.
2. Open the IDE plugin settings.
3. Select **Install Plugin from Disk**.
4. Choose the downloaded `.zip` file.
5. Restart the IDE if required.

---

# Agent Skill

This plugin does **not** ship an AI agent. Install your preferred coding agent, then install the Code Trace Tree skill. Once installed, the agent can **auto-load** it when your request is relevant. The skill is general — any agent that can load skill folders can use it (for example Claude Code, Cursor, GitHub Copilot, Codex, or Gemini CLI).

The skill lets the agent:

* Resolve the bound global storage XML for the project
* Search, add, move, and delete trace points
* Rebind line locations after source edits on disk
* Ask the IDE to reload / refresh plugin data
* Select or navigate to nodes in the Code Trace Tree tool window

## Agent Skill Installation

**Python 3 required:** skill scripts need **Python 3** on `PATH` (`python3` or `python`).
**Choose agents** and **Install / Update** stay disabled until Python 3 is found.
[Install Python 3](https://www.python.org/downloads/), add it to PATH, then restart the IDE.

Open the **Code Trace Tree** tool window and click **Agent Skill** on the toolbar (download icon):

![Click Agent Skill on the Code Trace Tree toolbar](https://raw.githubusercontent.com/pidifa/code-trace-tree-jetbrains/main/docs/assets/jetbrains-install-skill-1.png)

* The status page shows the bundled skill version, whether **Python 3** is on `PATH`, and which agents already have the skill.
* **Choose agents to install** — pick agents that do not have it yet.
* **Install / Update** — copy the bundled `code-trace-tree` skill into each listed agent's global skills folder (for example `~/.cursor/skills/code-trace-tree`).
* **Remove from installed agents** — delete those copies.

![Agent Skill status page](https://raw.githubusercontent.com/pidifa/code-trace-tree-jetbrains/main/docs/assets/jetbrains-install-skill-2.png)

On first project open, if a detected agent is missing the skill or is behind the bundled version, the plugin shows a notification (once per skill version; **Dismiss** or opening the status page is remembered in global `settings.xml`).

## How to use the skill

The agent loads global or project-local skills (including the installed `code-trace-tree`).
Use it in your agent chat:

```
skill: code-trace-tree
Help me generate some simple trace points related to the current topic.
```

```
skill: code-trace-tree
Add simple trace points along the call path of method `test`.
```
