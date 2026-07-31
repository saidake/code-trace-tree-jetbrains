import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.pidifa.codetracetree"
val pluginVersion = "1.0.7"
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
            <h3>1.0.7</h3>
            <ul>
              <li>Add file and directory trace points from the Project View</li>
              <li>Introduce <code>traceType</code> (<code>LINE</code> / <code>FILE</code> / <code>DIRECTORY</code>) with <code>traceName</code>, <code>baseName</code>, and <code>tracePath</code></li>
              <li>Support descriptions for all trace types; remove legacy config migration</li>
            </ul>
            <h3>1.0.6</h3>
            <ul>
              <li>Reload plugin data when the global storage XML changes or <code>.idea/code-trace-tree.refresh-request</code> is written</li>
              <li>Add Claude Code skill and scripts so agents can resolve storage, edit traces, and notify IDEA to refresh</li>
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
            sinceBuild = "251"
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
