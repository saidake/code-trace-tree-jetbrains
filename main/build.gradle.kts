import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.pidifa.codetracetree"
val pluginVersion = "1.0.3"
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
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(rootProject.layout.projectDirectory.file("README.md")).asText.map {
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"

            with(it.lines()) {
                if (!containsAll(listOf(start, end))) {
                    throw GradleException("Plugin description section not found in README.md:\n$start ... $end")
                }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n")
            }
        }

        changeNotes = """
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
