import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "com.pidifa.codetracetree"
val pluginVersion = "1.0.0"
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
    pluginConfiguration {
        name = "Code Trace Tree"
        version = pluginVersion
        // Extract the <!-- Plugin description --> section from README.md and provide for the plugin's manifest
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map {
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
            Initial version
        """.trimIndent()

        ideaVersion {
            sinceBuild = "251"
            untilBuild = "253.*"
        }
    }
}

tasks {
    buildPlugin {
        archiveFileName.set("code-trace-tree-$pluginVersion.zip")
    }
}
