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

        // Convert main/CHANGELOG.md (same markdown shape as VS Code) to Marketplace HTML
        changeNotes = providers.fileContents(layout.projectDirectory.file("CHANGELOG.md")).asText.map {
            changelogMarkdownToHtml(it)
        }

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

fun changelogMarkdownToHtml(markdown: String): String {
    fun escape(text: String) =
        text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    fun inline(text: String): String {
        var s = escape(text)
        s = Regex("""\*\*(.+?)\*\*""").replace(s) { "<b>${it.groupValues[1]}</b>" }
        s = Regex("""`([^`]+)`""").replace(s) { "<code>${it.groupValues[1]}</code>" }
        return s
    }

    val out = StringBuilder()
    var inList = false
    for (raw in markdown.lines()) {
        val line = raw.trim()
        when {
            line.isEmpty() || line.startsWith("# ") -> continue
            line.startsWith("## ") -> {
                if (inList) {
                    out.appendLine("</ul>")
                    inList = false
                }
                val version = line.removePrefix("## ").trim().removePrefix("v")
                out.appendLine("<h3>$version</h3>")
            }
            line.startsWith("- ") -> {
                if (!inList) {
                    out.appendLine("<ul>")
                    inList = true
                }
                out.appendLine("  <li>${inline(line.removePrefix("- "))}</li>")
            }
        }
    }
    if (inList) out.appendLine("</ul>")
    val html = out.toString().trim()
    if (html.isEmpty()) {
        throw GradleException("CHANGELOG.md did not produce any change notes")
    }
    return html
}

