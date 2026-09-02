/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.skill

data class AgentDef(
    val id: String,
    val label: String,
    val detect: List<String>,
    val globalSkills: String,
)

enum class AgentSkillState { MISSING, OUTDATED, LATEST, NEWER }

enum class AgentSkillNoticeStatus { DISMISSED, OPENED }

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

    /** Same agents and global paths as `npx skills` (https://github.com/vercel-labs/skills/blob/main/src/agents.ts). */
    val AGENTS: List<AgentDef> = listOf(
        ag("aider-desk", "AiderDesk", listOf(".aider-desk"), ".aider-desk/skills"),
        ag("amp", "Amp", listOf("xdg:amp"), "xdg:agents/skills"),
        ag("antigravity", "Antigravity", listOf(".gemini/antigravity"), ".gemini/antigravity/skills"),
        ag("antigravity-cli", "Antigravity CLI", listOf(".gemini/antigravity-cli"), ".gemini/antigravity-cli/skills"),
        ag("astrbot", "AstrBot", listOf(".astrbot"), ".astrbot/data/skills"),
        ag("autohand-code", "Autohand Code CLI", listOf(".autohand"), ".autohand/skills"),
        ag("augment", "Augment", listOf(".augment"), ".augment/skills"),
        ag("bob", "IBM Bob", listOf(".bob"), ".bob/skills"),
        ag("claude-code", "Claude Code", listOf(".claude"), ".claude/skills"),
        ag("openclaw", "OpenClaw", listOf(".openclaw", ".clawdbot", ".moltbot"), ".openclaw/skills"),
        ag("cline", "Cline", listOf(".cline"), ".agents/skills"),
        ag("codearts-agent", "CodeArts Agent", listOf(".codeartsdoer"), ".codeartsdoer/skills"),
        ag("codebuddy", "CodeBuddy", listOf(".codebuddy"), ".codebuddy/skills"),
        ag("codemaker", "Codemaker", listOf(".codemaker"), ".codemaker/skills"),
        ag("codestudio", "Code Studio", listOf(".codestudio"), ".codestudio/skills"),
        ag("codex", "Codex", listOf(".codex"), ".codex/skills"),
        ag("command-code", "Command Code", listOf(".commandcode"), ".commandcode/skills"),
        ag("continue", "Continue", listOf(".continue"), ".continue/skills"),
        ag("cortex", "Cortex Code", listOf(".snowflake/cortex"), ".snowflake/cortex/skills"),
        ag("crush", "Crush", listOf(".config/crush"), ".config/crush/skills"),
        ag("cursor", "Cursor", listOf(".cursor"), ".cursor/skills"),
        ag("deepagents", "Deep Agents", listOf(".deepagents"), ".deepagents/agent/skills"),
        ag("devin", "Devin for Terminal", listOf("xdg:devin"), "xdg:devin/skills"),
        ag("dexto", "Dexto", listOf(".dexto"), ".agents/skills"),
        ag("droid", "Droid", listOf(".factory"), ".factory/skills"),
        ag("firebender", "Firebender", listOf(".firebender"), ".firebender/skills"),
        ag("forgecode", "ForgeCode", listOf(".forge"), ".forge/skills"),
        ag("gemini-cli", "Gemini CLI", listOf(".gemini"), ".gemini/skills"),
        ag("github-copilot", "GitHub Copilot", listOf(".copilot"), ".copilot/skills"),
        ag("goose", "Goose", listOf("xdg:goose"), "xdg:goose/skills"),
        ag("grok", "Grok Build", listOf(".grok"), ".grok/skills"),
        ag("hermes-agent", "Hermes Agent", listOf(".hermes"), ".hermes/skills"),
        ag("inference-sh", "inference.sh", listOf(".inferencesh"), ".inferencesh/skills"),
        ag("jazz", "Jazz", listOf(".jazz"), ".jazz/skills"),
        ag("junie", "Junie", listOf(".junie"), ".junie/skills"),
        ag("iflow-cli", "iFlow CLI", listOf(".iflow"), ".iflow/skills"),
        ag("kilo", "Kilo Code", listOf(".kilocode"), ".kilocode/skills"),
        ag("kimchi", "Kimchi", listOf(".config/kimchi"), ".config/kimchi/harness/skills"),
        ag("kimi-code-cli", "Kimi Code CLI", listOf(".kimi-code", ".kimi"), ".agents/skills"),
        ag("kiro-cli", "Kiro CLI", listOf(".kiro"), ".kiro/skills"),
        ag("kode", "Kode", listOf(".kode"), ".kode/skills"),
        ag("lingma", "Lingma", listOf(".lingma"), ".lingma/skills"),
        ag("loaf", "Loaf", listOf(".loaf"), ".agents/skills"),
        ag("mcpjam", "MCPJam", listOf(".mcpjam"), ".mcpjam/skills"),
        ag("minimax-code", "MiniMax Code", listOf(".minimax"), ".minimax/skills"),
        ag("mistral-vibe", "Mistral Vibe", listOf(".vibe"), ".vibe/skills"),
        ag("moxby", "Moxby", listOf(".moxby"), ".moxby/skills"),
        ag("mux", "Mux", listOf(".mux"), ".mux/skills"),
        ag("opencode", "OpenCode", listOf("xdg:opencode"), "xdg:opencode/skills"),
        ag("openhands", "OpenHands", listOf(".openhands"), ".openhands/skills"),
        ag("ona", "Ona", listOf(".ona"), ".ona/skills"),
        ag("pi", "Pi", listOf(".pi/agent"), ".pi/agent/skills"),
        ag("posit-assistant", "Posit Assistant", listOf(".posit/assistant", ".positai"), ".posit/assistant/skills"),
        ag("qoder", "Qoder", listOf(".qoder"), ".qoder/skills"),
        ag("qoder-cn", "Qoder CN", listOf(".qoder-cn"), ".qoder-cn/skills"),
        ag("qwen-code", "Qwen Code", listOf(".qwen"), ".qwen/skills"),
        ag("replit", "Replit", emptyList(), "xdg:agents/skills"),
        ag("reasonix", "Reasonix", listOf(".reasonix"), ".reasonix/skills"),
        ag("rovodev", "Rovo Dev", listOf(".rovodev"), ".rovodev/skills"),
        ag("roo", "Roo Code", listOf(".roo"), ".roo/skills"),
        ag("tabnine-cli", "Tabnine CLI", listOf(".tabnine"), ".tabnine/agent/skills"),
        ag("terramind", "Terramind", listOf(".terramind"), ".terramind/skills"),
        ag("tinycloud", "Tinycloud", listOf(".tinycloud"), ".tinycloud/skills"),
        ag("trae", "Trae", listOf(".trae"), ".trae/skills"),
        ag("trae-cn", "Trae CN", listOf(".trae-cn"), ".trae-cn/skills"),
        ag("warp", "Warp", listOf(".warp"), ".agents/skills"),
        ag("windsurf", "Windsurf", listOf(".codeium/windsurf"), ".codeium/windsurf/skills"),
        ag("zed", "Zed", listOf("xdg:zed"), ".agents/skills"),
        ag("zcode", "ZCode", listOf(".zcode"), ".zcode/skills"),
        ag("zencoder", "Zencoder", listOf(".zencoder"), ".zencoder/skills"),
        ag("zenflow", "Zenflow", listOf(".zencoder"), ".zencoder/skills"),
        ag("neovate", "Neovate", listOf(".neovate"), ".neovate/skills"),
        ag("pochi", "Pochi", listOf(".pochi"), ".pochi/skills"),
        ag("adal", "AdaL", listOf(".adal"), ".adal/skills"),
        ag("universal", "Universal", emptyList(), "xdg:agents/skills"),
    )

    private fun ag(id: String, label: String, detect: List<String>, globalSkills: String) =
        AgentDef(id, label, detect, globalSkills)

    fun xdgConfigHome(homeDir: java.nio.file.Path): java.nio.file.Path {
        val env = System.getenv("XDG_CONFIG_HOME")?.trim().orEmpty()
        return if (env.isNotEmpty()) java.nio.file.Paths.get(env) else homeDir.resolve(".config")
    }

    fun resolveAgentLayout(
        def: AgentDef,
        homeDir: java.nio.file.Path = java.nio.file.Paths.get(System.getProperty("user.home")),
    ): Pair<List<java.nio.file.Path>, java.nio.file.Path> {
        fun resolveSpec(spec: String): java.nio.file.Path {
            if (spec.startsWith("xdg:")) return resolveRel(xdgConfigHome(homeDir), spec.removePrefix("xdg:"))
            val raw = java.nio.file.Paths.get(spec)
            if (raw.isAbsolute) return raw
            return resolveRel(homeDir, spec)
        }
        fun envHome(envName: String, fallbackRel: String): java.nio.file.Path {
            val env = System.getenv(envName)?.trim().orEmpty()
            return if (env.isNotEmpty()) java.nio.file.Paths.get(env) else resolveRel(homeDir, fallbackRel)
        }
        var detectPaths = def.detect.map(::resolveSpec)
        var skillsDir = resolveSpec(def.globalSkills)
        when (def.id) {
            "claude-code" -> {
                val h = envHome("CLAUDE_CONFIG_DIR", ".claude")
                detectPaths = listOf(h)
                skillsDir = h.resolve("skills")
            }
            "codex" -> {
                val h = envHome("CODEX_HOME", ".codex")
                detectPaths = listOf(h, java.nio.file.Paths.get("/etc/codex"))
                skillsDir = h.resolve("skills")
            }
            "autohand-code" -> {
                val h = envHome("AUTOHAND_HOME", ".autohand")
                detectPaths = listOf(h)
                skillsDir = h.resolve("skills")
            }
            "grok" -> {
                val h = envHome("GROK_HOME", ".grok")
                detectPaths = listOf(h)
                skillsDir = h.resolve("skills")
            }
            "hermes-agent" -> {
                val h = envHome("HERMES_HOME", ".hermes")
                detectPaths = listOf(h)
                skillsDir = h.resolve("skills")
            }
            "mistral-vibe" -> {
                val h = envHome("VIBE_HOME", ".vibe")
                detectPaths = listOf(h)
                skillsDir = h.resolve("skills")
            }
            "openclaw" -> {
                val candidates = listOf(".openclaw", ".clawdbot", ".moltbot").map { resolveRel(homeDir, it) }
                detectPaths = candidates
                val found = candidates.firstOrNull { java.nio.file.Files.exists(it) }
                skillsDir = (found ?: resolveRel(homeDir, ".openclaw")).resolve("skills")
            }
            "zed" -> {
                val paths = mutableListOf(resolveRel(xdgConfigHome(homeDir), "zed"))
                System.getenv("APPDATA")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    paths.add(java.nio.file.Paths.get(it, "Zed"))
                }
                System.getenv("FLATPAK_XDG_CONFIG_HOME")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    paths.add(java.nio.file.Paths.get(it, "zed"))
                }
                detectPaths = paths
            }
            "minimax-code" -> detectPaths = detectPaths + java.nio.file.Paths.get("/Applications/MiniMax Code.app")
            "zcode" -> detectPaths = detectPaths + java.nio.file.Paths.get("/Applications/ZCode.app")
        }
        return detectPaths to skillsDir
    }

    private fun resolveRel(base: java.nio.file.Path, rel: String): java.nio.file.Path {
        var p = base
        for (part in rel.split('/').filter { it.isNotEmpty() }) p = p.resolve(part)
        return p
    }

    fun parseSkillVersion(skillMd: String): String? {
        val fm = Regex("^---\\r?\\n([\\s\\S]*?)\\r?\\n---").find(skillMd) ?: return null
        return Regex("""^version:\s*['"]?([0-9]+(?:\.[0-9]+)*)['"]?\s*$""", RegexOption.MULTILINE)
            .find(fm.groupValues[1])
            ?.groupValues
            ?.get(1)
    }

    fun compareSkillVersions(a: String?, b: String?): Int = skillVersionRank(a) - skillVersionRank(b)

    /** Missing, empty, or non-integer values count as 0. Integers compare as-is. */
    private fun skillVersionRank(raw: String?): Int {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty() || !t.matches(Regex("^\\d+$"))) return 0
        return t.toIntOrNull() ?: 0
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
            val (detectPaths, skillsDir) = resolveAgentLayout(def, homeDir)
            val detected = detectPaths.any { java.nio.file.Files.exists(it) }
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

    /** True when a detected agent is missing/outdated and this bundled version has not been dismissed or opened. */
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
            val dest = resolveAgentLayout(def, homeDir).second.resolve(SKILL_FOLDER)
            copySkillDir(bundledDir, dest)
            results += id to dest
        }
        return results
    }

    /** Delete the bundled skill folder from each listed agent's global skills directory. */
    fun removeSkillForAgents(
        agentIds: Collection<String>,
        homeDir: java.nio.file.Path = java.nio.file.Paths.get(System.getProperty("user.home")),
    ): List<Pair<String, java.nio.file.Path>> {
        val results = mutableListOf<Pair<String, java.nio.file.Path>>()
        for (id in agentIds) {
            val def = AGENTS.find { it.id == id } ?: continue
            val dest = resolveAgentLayout(def, homeDir).second.resolve(SKILL_FOLDER)
            if (!java.nio.file.Files.exists(dest)) continue
            dest.toFile().deleteRecursively()
            results += id to dest
        }
        return results
    }

    fun agentsWithInstalledSkill(statuses: List<AgentSkillStatus>): List<AgentSkillStatus> =
        statuses.filter { it.state != AgentSkillState.MISSING }

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
