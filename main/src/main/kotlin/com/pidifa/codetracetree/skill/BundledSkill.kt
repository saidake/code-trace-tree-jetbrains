/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.skill

import java.net.JarURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

object BundledSkill {
    private const val RESOURCE_DIR = "/skills/code-trace-tree"
    private const val JAR_PREFIX = "skills/code-trace-tree/"

    fun skillMdText(): String {
        val stream = BundledSkill::class.java.getResourceAsStream("$RESOURCE_DIR/SKILL.md")
            ?: error("Bundled SKILL.md is missing")
        return stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
    }

    fun bundledVersion(): String? = AgentSkill.parseSkillVersion(skillMdText())

    fun copyTo(dest: Path) {
        val url = BundledSkill::class.java.getResource("$RESOURCE_DIR/SKILL.md")
            ?: error("Bundled skill is missing")
        when (url.protocol) {
            "file" -> AgentSkill.copySkillDir(Paths.get(url.toURI()).parent, dest)
            "jar" -> copyFromJar(url, dest)
            else -> error("Unsupported bundled skill URL: $url")
        }
    }

    private fun copyFromJar(skillMdUrl: URL, dest: Path) {
        if (Files.exists(dest)) dest.toFile().deleteRecursively()
        Files.createDirectories(dest)
        val conn = skillMdUrl.openConnection() as JarURLConnection
        val jar = conn.jarFile
        val entries = jar.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (!entry.name.startsWith(JAR_PREFIX) || entry.isDirectory) continue
            if (entry.name.contains("__pycache__") || entry.name.endsWith(".pyc")) continue
            val rel = entry.name.removePrefix(JAR_PREFIX)
            val out = dest.resolve(rel)
            Files.createDirectories(out.parent)
            jar.getInputStream(entry).use { input ->
                Files.copy(input, out, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        if (!Files.isRegularFile(dest.resolve("SKILL.md"))) {
            error("Bundled skill copy failed: SKILL.md missing at $dest")
        }
    }
}
