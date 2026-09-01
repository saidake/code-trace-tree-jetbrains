/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.skill

data class AgentDef(
    val id: String,
    val label: String,
    val homeMarker: String,
    val globalSkillsRel: String,
)

enum class AgentSkillState { MISSING, OUTDATED, LATEST, NEWER }

enum class AgentSkillNoticeStatus { DISMISSED, INSTALLED }

data class AgentSkillStatus(
    val id: String,
    val label: String,
    val detected: Boolean,
    val skillsDir: java.nio.file.Path,
    val installedVersion: String?,
    val state: AgentSkillState,
)

data class PythonStatus(
    val ready: Boolean,
    val command: String? = null,
    val version: String? = null,
)

object AgentSkill {
    const val SKILL_FOLDER = "code-trace-tree"

    val AGENTS: List<AgentDef> = listOf(
        AgentDef("claude-code", "Claude Code", ".claude", ".claude/skills"),
        AgentDef("cursor", "Cursor", ".cursor", ".cursor/skills"),
        AgentDef("github-copilot", "GitHub Copilot", ".copilot", ".copilot/skills"),
        AgentDef("codex", "Codex", ".agents", ".agents/skills"),
        AgentDef("gemini-cli", "Gemini CLI", ".gemini", ".gemini/skills"),
    )

    fun parseSkillVersion(skillMd: String): String? {
        val fm = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---").find(skillMd) ?: return null
        return Regex("""^version:\s*['"]?([0-9]+(?:\.[0-9]+)*)['"]?\s*$""", RegexOption.MULTILINE)
            .find(fm.groupValues[1])
            ?.groupValues
            ?.get(1)
    }

    fun compareSkillVersions(a: String?, b: String?): Int {
        val pa = parseVersionParts(a)
        val pb = parseVersionParts(b)
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val d = (pa.getOrElse(i) { 0 }) - (pb.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    private fun parseVersionParts(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return listOf(0)
        return raw.trim().split('.').map { it.toIntOrNull() ?: 0 }
    }

    fun readInstalledSkillVersion(skillDir: java.nio.file.Path): String? {
        val file = skillDir.resolve("SKILL.md")
        if (!java.nio.file.Files.isRegularFile(file)) return null
        return try {
            parseSkillVersion(java.nio.file.Files.readString(file))
        } catch (_: Exception) {
            null
        }
    }

    fun scanAgentStatuses(
        bundledVersion: String,
        homeDir: java.nio.file.Path = java.nio.file.Paths.get(System.getProperty("user.home")),
    ): List<AgentSkillStatus> {
        return AGENTS.map { def ->
            val marker = homeDir.resolve(def.homeMarker)
            val skillsDir = homeDir.resolve(def.globalSkillsRel)
            val detected = java.nio.file.Files.isDirectory(marker) || java.nio.file.Files.isDirectory(skillsDir)
            val skillDir = skillsDir.resolve(SKILL_FOLDER)
            val hasSkill = java.nio.file.Files.isRegularFile(skillDir.resolve("SKILL.md"))
            val installed = if (hasSkill) readInstalledSkillVersion(skillDir) else null
            val state = if (!hasSkill) {
                AgentSkillState.MISSING
            } else {
                val cmp = compareSkillVersions(installed, bundledVersion)
                when {
                    cmp < 0 -> AgentSkillState.OUTDATED
                    cmp > 0 -> AgentSkillState.NEWER
                    else -> AgentSkillState.LATEST
                }
            }
            AgentSkillStatus(def.id, def.label, detected, skillsDir, installed, state)
        }
    }

    fun shouldOfferSkillNotice(
        bundledVersion: String?,
        statuses: List<AgentSkillStatus>,
        lastHandledVersion: String?,
    ): Boolean {
        if (bundledVersion.isNullOrBlank()) return false
        if (lastHandledVersion != null && compareSkillVersions(lastHandledVersion, bundledVersion) >= 0) {
            return false
        }
        val detected = statuses.filter { it.detected }
        if (detected.isEmpty()) return false
        return detected.any { it.state == AgentSkillState.MISSING || it.state == AgentSkillState.OUTDATED }
    }

    fun copySkillDir(src: java.nio.file.Path, dest: java.nio.file.Path) {
        if (!java.nio.file.Files.isRegularFile(src.resolve("SKILL.md"))) {
            throw IllegalStateException("Bundled skill is missing SKILL.md at $src")
        }
        if (java.nio.file.Files.exists(dest)) {
            dest.toFile().deleteRecursively()
        }
        copyDir(src, dest)
    }

    fun installSkillForAgents(
        bundledDir: java.nio.file.Path,
        agentIds: Collection<String>,
        homeDir: java.nio.file.Path = java.nio.file.Paths.get(System.getProperty("user.home")),
    ): List<Pair<String, java.nio.file.Path>> {
        val results = mutableListOf<Pair<String, java.nio.file.Path>>()
        for (id in agentIds) {
            val def = AGENTS.find { it.id == id } ?: continue
            val dest = homeDir.resolve(def.globalSkillsRel).resolve(SKILL_FOLDER)
            copySkillDir(bundledDir, dest)
            results += id to dest
        }
        return results
    }

    fun detectPython3(): PythonStatus {
        val candidates = if (isWindows()) {
            listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
        } else {
            listOf(listOf("python3"), listOf("python"))
        }
        for (cmd in candidates) {
            val r = try {
                run(cmd + "--version")
            } catch (_: Exception) {
                continue
            }
            if (r.first != 0) continue
            val text = (r.second + " " + r.third).trim()
            val m = Regex("""Python\s+(3(?:\.\d+)*)""", RegexOption.IGNORE_CASE).find(text) ?: continue
            val command = cmd.joinToString(" ")
            return PythonStatus(ready = true, command = command, version = m.groupValues[1])
        }
        return PythonStatus(ready = false)
    }

    private fun run(command: List<String>): Triple<Int, String, String> {
        val pb = ProcessBuilder(command)
        pb.redirectErrorStream(false)
        val proc = pb.start()
        val stdout = proc.inputStream.readBytes().toString(Charsets.UTF_8)
        val stderr = proc.errorStream.readBytes().toString(Charsets.UTF_8)
        val done = proc.waitFor()
        return Triple(done, stdout, stderr)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun copyDir(src: java.nio.file.Path, dest: java.nio.file.Path) {
        java.nio.file.Files.createDirectories(dest)
        java.nio.file.Files.list(src).use { stream ->
            stream.forEach { child ->
                val name = child.fileName.toString()
                if (name == "__pycache__" || name.endsWith(".pyc")) return@forEach
                val to = dest.resolve(name)
                if (java.nio.file.Files.isDirectory(child)) copyDir(child, to)
                else java.nio.file.Files.copy(child, to)
            }
        }
    }
}
