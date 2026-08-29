/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.testsupport

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

object SkillProcess {
    fun findScriptsDir(): Path {
        var dir: Path? = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve("skills").resolve("code-trace-tree").resolve("scripts")
            if (Files.isRegularFile(candidate.resolve("trace_tree.py"))) return candidate
            dir = dir.parent
        }
        throw IllegalStateException(
            "skill scripts not found walking up from ${System.getProperty("user.dir")}"
        )
    }

    fun findPython(): List<String> {
        val candidates = if (isWindows()) {
            listOf(listOf("py", "-3"), listOf("python"), listOf("python3"))
        } else {
            listOf(listOf("python3"), listOf("python"))
        }
        for (cmd in candidates) {
            val r = try {
                run(cmd + listOf("-c", "print(1)"), cwd = Paths.get(".").toAbsolutePath(), appDirBase = Paths.get(".").toAbsolutePath())
            } catch (_: Exception) {
                continue
            }
            if (r.status == 0) return cmd
        }
        assumeTrue(false, "Python is required to run skill script tests (python / python3 / py -3)")
        return emptyList()
    }

    fun run(
        command: List<String>,
        cwd: Path,
        appDirBase: Path,
    ): Result {
        val pb = ProcessBuilder(command)
        pb.directory(cwd.toFile())
        pb.redirectErrorStream(false)
        val env = pb.environment()
        env["LOCALAPPDATA"] = appDirBase.toString()
        env["XDG_CONFIG_HOME"] = appDirBase.toString()
        val proc = pb.start()
        val stdout = proc.inputStream.readBytes().toString(StandardCharsets.UTF_8)
        val stderr = proc.errorStream.readBytes().toString(StandardCharsets.UTF_8)
        val status = proc.waitFor()
        return Result(status, stdout, stderr)
    }

    fun runScript(
        python: List<String>,
        scriptName: String,
        args: List<String>,
        cwd: Path,
        appDirBase: Path,
    ): Result {
        val script = findScriptsDir().resolve(scriptName).toString()
        return run(python + listOf(script) + args, cwd, appDirBase)
    }

    fun makeTempProject(): Fixture {
        val root = Files.createTempDirectory("ctt-skill-")
        val projectRoot = root.resolve("project")
        val appDirBase = root.resolve("appdata")
        Files.createDirectories(projectRoot.resolve(".git"))
        Files.createDirectories(projectRoot.resolve("src"))
        Files.writeString(
            projectRoot.resolve("src").resolve("app.py"),
            "def alpha():\n    pass\n\ndef beta():\n    pass\n",
        )
        return Fixture(root, projectRoot, appDirBase)
    }

    fun parseJsonField(stdout: String, key: String): String {
        val start = stdout.indexOf('{')
        require(start >= 0) { "no JSON in stdout: $stdout" }
        val json = stdout.substring(start)
        val regex = Regex("\"$key\"\\s*:\\s*(\"(?:\\\\.|[^\"\\\\])*\"|true|false|null|-?\\d+(?:\\.\\d+)?)")
        val match = regex.find(json) ?: throw IllegalArgumentException("missing $key in $json")
        var raw = match.groupValues[1]
        if (raw.startsWith("\"") && raw.endsWith("\"")) {
            raw = raw.substring(1, raw.length - 1).replace("\\\\", "\\").replace("\\\"", "\"")
        }
        return raw
    }

    fun projectIdFromXml(xmlPath: Path): String {
        val xml = Files.readString(xmlPath)
        val match = Regex("<projectId>([^<]+)</projectId>").find(xml)
            ?: throw IllegalStateException("no projectId in $xmlPath")
        return match.groupValues[1]
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    data class Result(val status: Int, val stdout: String, val stderr: String)

    data class Fixture(val root: Path, val projectRoot: Path, val appDirBase: Path) {
        fun cleanup() {
            root.toFile().deleteRecursively()
        }

        fun signalsDir(): Path = appDirBase.resolve("code-trace-tree").resolve("signals")
    }
}
