import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.pidifa.codetracetree"
val pluginVersion = "1.3.2"
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    projectName = "code-trace-tree"
    // No custom Settings UI; skip headless IDE searchable-options indexing
    buildSearchableOptions = false

    pluginConfiguration {
        name = "Code Trace Tree"
        version = pluginVersion
        // Extract all <!-- Plugin description --> sections from README.md and provide for the plugin's manifest
        description = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            val lines = it.lines()
            val sections = mutableListOf<String>()
            var i = 0
            while (i < lines.size) {
                val startIdx = lines.subList(i, lines.size).indexOf(start).takeIf { it >= 0 }?.plus(i) ?: break
                val endIdx = lines.subList(startIdx + 1, lines.size).indexOf(end).takeIf { it >= 0 }?.plus(startIdx + 1)
                    ?: throw GradleException("Unclosed plugin description section in README.md:\n$start ... $end")
                sections += lines.subList(startIdx + 1, endIdx).joinToString("\n")
                i = endIdx + 1
            }
            if (sections.isEmpty()) {
                throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
            }
            sections.joinToString("\n")
        }

        changeNotes = """
            <h3>1.3.2</h3>
            <ul>
              <li>Toolbar Advanced Settings uses a gear icon</li>
              <li>Tree context menu <b>Go to Trace Point</b> is first (navigates the clicked node)</li>
              <li>Install the Agent Skill from the dedicated repo: <code>npx skills add saidake/code-trace-tree-skill</code></li>
              <li>Add a second Marketplace preview screenshot</li>
            </ul>
            <h3>1.3.1</h3>
            <ul>
              <li>Highlight line colors are a global preference (<code>settings.xml</code>), shared across projects and IDEs; dark default is <code>#236C60</code></li>
              <li>First Advanced Settings save creates <code>settings.xml</code> and migrates leftover project colors; peers reload via <code>request_refresh_global_settings</code></li>
            </ul>
            <h3>1.3.0</h3>
            <ul>
              <li>Split skill <code>add</code> vs <code>ensure</code>; slim skill docs to script ops</li>
              <li>Disable trace highlights and editor context menus in Git / IDE diff panes</li>
              <li>Skill: do not delete existing traces unless the user asks</li>
              <li>Clarify Agent Skill install: extract the zip into the agent skills directory (replace <code>code-trace-tree</code> if present)</li>
              <li>Update Marketplace preview screenshot</li>
            </ul>
            <h3>1.2.10</h3>
            <ul>
              <li>Expand the drop-target parent after drag-and-drop reparent so the moved child stays visible</li>
            </ul>
            <h3>1.2.9</h3>
            <ul>
              <li><b>Recheck Trace Availability</b>: reload bound XML and validate all traces; tiered peer refresh (full / profile / settings)</li>
              <li>Rebind LINE traces on file open; content-rebind after bulk external edits</li>
              <li>Toolbar: <b>Remove Invalid Trace Points</b>; Import/Export moved into Advanced Settings; context menu <b>Copy Label</b></li>
              <li>Preserve tree selection across self profile-refresh echoes; scope disk watching to open LINE buffers and path tips</li>
            </ul>
            <h3>1.2.8</h3>
            <ul>
              <li>Update Marketplace plugin icons (light/dark)</li>
              <li>Add Plugin home page button linking to JetBrains Marketplace</li>
              <li>Document the Agent Skill as agent-agnostic (listed agents are examples)</li>
              <li>Preferred workflow format: nest by call flow (sibling fan-out under a call)</li>
              <li>Clarify skill auto-load (project/global) and real-time IDE sync in README</li>
              <li>Align version with VS Code / Cursor companions</li>
            </ul>
            <h3>1.2.6</h3>
            <ul>
              <li>Align version with VS Code / Cursor companions (Trace Points webview list on those IDEs)</li>
            </ul>
            <h3>1.2.5</h3>
            <ul>
              <li>Editor and Project View Code Trace Tree actions only for files under the project root (relative <code>tracePath</code>)</li>
              <li>After create, select the new node in the tree without jumping to source</li>
              <li>Agent path-mode storage: <code>storage-ready</code> carries project path; reuse existing <code>.idea</code> id; recreate missing XML with the same id</li>
            </ul>
            <h3>1.2.4</h3>
            <ul>
              <li>Always show <b>Go to the Trace Point in the tree panel (Only matching)</b> in the editor context menu; no-ops when nothing matches</li>
            </ul>
            <h3>1.2.3</h3>
            <ul>
              <li>Advanced Settings: persist highlight line background colors (light/dark) in shared project XML</li>
              <li>Toolbar Advanced Settings action; editor highlights use the configured theme colors</li>
              <li>Maximize Description toolbar toggle; resizable description/tree splitter</li>
              <li>Expand Marketplace intro for building and displaying code workflows</li>
            </ul>
            <h3>1.2.1</h3>
            <ul>
              <li>Idea-only project id (<code>.idea/code-trace-tree.project.id</code>); Case B path bind reuses the latest matching XML or copy-on-writes a new UUID when several match</li>
              <li>Case C lazy create overwrites a stale idea id with a new UUID XML</li>
              <li>Simplify Marketplace intro copy</li>
            </ul>
            <h3>1.2.0</h3>
            <ul>
              <li>Bind Case C (unbound) windows via global <code>&lt;projectId&gt;.storage-ready</code> when agents create storage</li>
              <li>Poll agent signal files so rapid refreshes are not missed on Windows</li>
              <li>Agent-driven reloads bypass the self-write ignore window</li>
            </ul>
            <h3>1.1.12</h3>
            <ul>
              <li>Align README Agent Skill install links and zip names with v1.1.12</li>
            </ul>
            <h3>1.1.11</h3>
            <ul>
              <li>Align version with the VS Code companion (jump from 1.1.8; no separate JetBrains 1.1.9 / 1.1.10 builds)</li>
              <li>Lazy project storage (Case C): create storage on first real use</li>
              <li>Agent signals: <code>request_refresh</code> and <code>request_refresh_profile</code> (no XML file watch)</li>
              <li>Tree context menu <b>Show Line Content</b> for LINE nodes (copyable)</li>
              <li>Block creating or updating LINE traces on empty lines</li>
              <li>Update README preview and badges</li>
            </ul>
            <h3>1.1.8</h3>
            <ul>
              <li>Agents edit traces only when asked via the skill (no auto-sync toolbar toggle)</li>
              <li>Document how to prompt the <code>code-trace-tree</code> skill in the README</li>
            </ul>
            <h3>1.1.7</h3>
            <ul>
              <li>Reset the description area when switching profiles</li>
              <li>Fix empty Code Trace Tree tool window ("Nothing to show") caused by refreshing description before the tree was initialized</li>
            </ul>
            <h3>1.1.6</h3>
            <ul>
              <li>Agent Skill: do not refuse OS Config Dir writes as outside-workspace</li>
              <li>Agent Skill: add repeatable <code>--parent-id</code>; disambiguate duplicate LINE tips by occurrence</li>
              <li>Agent Skill: annotated multi-profile XML example</li>
            </ul>
            <h3>1.1.5</h3>
            <ul>
              <li>Store project data as <code>&lt;projectId&gt;.xml</code>; still resolve and rename legacy <code>&lt;FolderName&gt;.xml</code></li>
              <li>Move agent refresh/select signals to global <code>signals/&lt;projectId&gt;.*</code> with a 60s TTL (multi-window safe)</li>
              <li>Ship one shared Agent Skill zip (<code>code-trace-tree-skill-X.Y.Z.zip</code>); clarify Agent Skill Path and OS Config Dir in the skill</li>
              <li>Forgiving LINE locators, idempotent add, and absolute skill script paths</li>
            </ul>
            <h3>1.1.4</h3>
            <ul>
              <li>Lower IDE compatibility floor to IntelliJ Platform 2024.1 (<code>sinceBuild</code> 241)</li>
            </ul>
            <h3>1.1.3</h3>
            <ul>
              <li>Initialize project id and default <code>main</code> profile as soon as a project opens</li>
              <li>Clarify Agent Skill install commands in the Marketplace description</li>
            </ul>
            <h3>1.1.2</h3>
            <ul>
              <li>Add agent select-request signal to select/reveal nodes and navigate when exactly one id is listed</li>
              <li>Add <code>trace_tree</code> skill scripts for search/add/move/delete/rebind (no occurrence args from the agent)</li>
              <li>Rebind LINE locations after disk edits (skill + IDE VFS content rebind)</li>
              <li>Replace skill Python helpers with shell/batch scripts; expand Agent Skill docs and install instructions</li>
            </ul>
            <h3>1.1.1</h3>
            <ul>
              <li>Color trace point names in the tree and add a space before the location suffix</li>
              <li>Copy a node's display text from the context menu or with Ctrl/Cmd+C</li>
              <li>Add a toolbar toggle to skip the name prompt when creating trace points</li>
              <li>Remove the optional description dialog when creating file or directory traces</li>
              <li>Update Marketplace plugin icons</li>
            </ul>
            <h3>1.1.0</h3>
            <ul>
              <li>Update Marketplace plugin icons</li>
            </ul>
            <h3>1.0.9</h3>
            <ul>
              <li>Set Marketplace plugin icons to 40x40</li>
              <li>Format storage folder paths as a list in the plugin description</li>
            </ul>
            <h3>1.0.8</h3>
            <ul>
              <li>Update Marketplace plugin logo</li>
              <li>Attach Agent Skill ZIP to GitHub Releases for easier agent install</li>
            </ul>
            <h3>1.0.7</h3>
            <ul>
              <li>Add file and directory trace points from the Project View</li>
              <li>Introduce <code>traceType</code> (<code>LINE</code> / <code>FILE</code> / <code>DIRECTORY</code>) with <code>traceName</code>, <code>baseName</code>, and <code>tracePath</code></li>
              <li>Support descriptions for all trace types; remove legacy config migration</li>
            </ul>
            <h3>1.0.6</h3>
            <ul>
              <li>Reload plugin data when the global storage XML changes or <code>.idea/code-trace-tree.refresh-request</code> is written</li>
              <li>Add Agent Skill and scripts so agents can resolve storage, edit traces, and notify IDEA to refresh</li>
            </ul>
            <h3>1.0.5</h3>
            <ul>
              <li>Store trace data in OS global config (<code>%LOCALAPPDATA%</code> / Application Support / XDG) with a project id file under <code>.idea</code></li>
              <li>Share-friendly storage and export XML (no per-node <code>projectPath</code> / <code>isValid</code>); single export uses <code>&lt;traceProfile&gt;</code></li>
              <li>Document storage location and manual cleanup in the plugin description</li>
            </ul>
            <h3>1.0.4</h3>
            <ul>
              <li>Include How to use instructions in the Marketplace plugin description</li>
            </ul>
            <h3>1.0.3</h3>
            <ul>
              <li>Update plugin logos for Marketplace and Plugin Manager</li>
            </ul>
            <h3>1.0.2</h3>
            <ul>
              <li>Add Marketplace / Plugin Manager logos (<code>pluginIcon.svg</code>)</li>
            </ul>
            <h3>1.0.1</h3>
            <ul>
              <li>Add Trace Profiles so you can keep multiple independent trace trees (default: <code>main</code>)</li>
              <li>Add, switch, and delete profiles from the tool window</li>
              <li>Export the current profile or all profiles; import with explicit replace / new / merge choices</li>
            </ul>
            <h3>1.0.0</h3>
            <ul>
              <li>Initial Marketplace release</li>
            </ul>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "241" // IntelliJ Platform 2024.1+
            // Open-ended: compatible with all IDE builds from sinceBuild onward (incl. latest)
            untilBuild = provider { null }
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    // Avoid Gradle module name "main" becoming the ZIP root / JAR base name
    jar {
        archiveBaseName.set("code-trace-tree")
    }
    instrumentedJar {
        archiveBaseName.set("code-trace-tree")
    }
    composedJar {
        archiveBaseName.set("code-trace-tree")
    }
    prepareSandbox {
        pluginName.set("code-trace-tree")
    }
    buildPlugin {
        archiveFileName.set("code-trace-tree-$pluginVersion.zip")
    }
}
