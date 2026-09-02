/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

import com.pidifa.codetracetree.testsupport.SkillProcess
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class SkillScriptsTest {
    private val python = SkillProcess.findPython()
    private lateinit var fixture: SkillProcess.Fixture

    @BeforeEach
    fun setUp() {
        fixture = SkillProcess.makeTempProject()
    }

    @AfterEach
    fun tearDown() {
        fixture.cleanup()
    }

    @Test
    fun `resolve_storage creates XML under the isolated app dir`() {
        val r = SkillProcess.runScript(
            python,
            "resolve_storage.py",
            listOf(fixture.projectRoot.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, r.status, r.stderr + r.stdout)
        assertEquals("true", SkillProcess.parseJsonField(r.stdout, "created"))
        val xml = SkillProcess.parseJsonField(r.stdout, "storage_xml")
        assertTrue(Files.exists(java.nio.file.Paths.get(xml)), xml)
        val globalDir = SkillProcess.parseJsonField(r.stdout, "global_dir")
        assertTrue(globalDir.contains("code-trace-tree"), globalDir)
    }

    @Test
    fun `trace_tree add writes a LINE node and notifies the IDE`() {
        val r = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf(
                "add",
                "--project", fixture.projectRoot.toString(),
                "--file", "src/app.py",
                "--line", "1",
                "--content", "def alpha():",
                "--trace-name", "alpha",
            ),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, r.status, r.stderr + r.stdout)
        assertEquals("add", SkillProcess.parseJsonField(r.stdout, "action"))
        assertEquals("true", SkillProcess.parseJsonField(r.stdout, "refreshed"))
        val xmlPath = java.nio.file.Paths.get(SkillProcess.parseJsonField(r.stdout, "storage_xml"))
        val projectId = SkillProcess.projectIdFromXml(xmlPath)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh_profile")))
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.storage-ready")))
    }

    @Test
    fun `trace_tree rename updates traceName and notifies the IDE`() {
        val add = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf(
                "add",
                "--project", fixture.projectRoot.toString(),
                "--file", "src/app.py",
                "--line", "1",
                "--content", "def alpha():",
                "--trace-name", "alpha",
            ),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, add.status, add.stderr + add.stdout)
        val nodeId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(add.stdout)?.groupValues?.get(1)
            ?: throw IllegalStateException(add.stdout)
        val xmlPath = java.nio.file.Paths.get(SkillProcess.parseJsonField(add.stdout, "storage_xml"))
        val projectId = SkillProcess.projectIdFromXml(xmlPath)
        val rename = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf(
                "rename",
                "--project", fixture.projectRoot.toString(),
                "--id", nodeId,
                "--trace-name", "alpha-renamed",
            ),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, rename.status, rename.stderr + rename.stdout)
        assertEquals("rename", SkillProcess.parseJsonField(rename.stdout, "action"))
        val xml = Files.readString(xmlPath)
        assertTrue(xml.contains("<traceName>alpha-renamed</traceName>"), xml)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh_profile")))
    }

    @Test
    fun `trace_tree rebind updates the line after an external disk edit and notifies the IDE`() {
        val add = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf(
                "add",
                "--project", fixture.projectRoot.toString(),
                "--file", "src/app.py",
                "--line", "1",
                "--content", "def alpha():",
                "--trace-name", "alpha",
            ),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, add.status, add.stderr + add.stdout)
        val xmlPath = java.nio.file.Paths.get(SkillProcess.parseJsonField(add.stdout, "storage_xml"))
        Files.writeString(
            fixture.projectRoot.resolve("src").resolve("app.py"),
            "header()\n\ndef alpha():\n    pass\n",
        )
        val rebind = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf("rebind", "--project", fixture.projectRoot.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, rebind.status, rebind.stderr + rebind.stdout)
        val xml = Files.readString(xmlPath)
        assertTrue(xml.contains("<lineNumber>3</lineNumber>"), xml)
        val projectId = SkillProcess.projectIdFromXml(xmlPath)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh_profile")))
    }

    @Test
    fun `request_refresh scripts write IDE signal files`() {
        val resolve = SkillProcess.runScript(
            python,
            "resolve_storage.py",
            listOf(fixture.projectRoot.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, resolve.status, resolve.stderr + resolve.stdout)
        val projectId = SkillProcess.parseJsonField(resolve.stdout, "project_id")

        val refresh = SkillProcess.runScript(
            python,
            "request_refresh.py",
            listOf(fixture.projectRoot.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, refresh.status, refresh.stderr + refresh.stdout)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh")))
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.storage-ready")))

        val profile = SkillProcess.runScript(
            python,
            "request_refresh_profile.py",
            listOf(fixture.projectRoot.toString(), "main"),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, profile.status, profile.stderr + profile.stdout)
        assertEquals(
            "main",
            Files.readString(fixture.signalsDir().resolve("$projectId.request_refresh_profile")).trim(),
        )

        val settings = SkillProcess.runScript(
            python,
            "request_refresh_settings.py",
            listOf(fixture.projectRoot.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, settings.status, settings.stderr + settings.stdout)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh_settings")))
    }

    @Test
    fun `select_trace_points writes a select signal for the IDE`() {
        val add = SkillProcess.runScript(
            python,
            "trace_tree.py",
            listOf(
                "add",
                "--project", fixture.projectRoot.toString(),
                "--file", "src/app.py",
                "--line", "1",
                "--content", "def alpha():",
            ),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, add.status, add.stderr + add.stdout)
        val nodeId = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"").find(add.stdout)?.groupValues?.get(1)
            ?: throw IllegalStateException(add.stdout)
        val xmlPath = java.nio.file.Paths.get(SkillProcess.parseJsonField(add.stdout, "storage_xml"))
        val projectId = SkillProcess.projectIdFromXml(xmlPath)
        val select = SkillProcess.runScript(
            python,
            "select_trace_points.py",
            listOf(nodeId),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, select.status, select.stderr + select.stdout)
        val body = Files.readString(fixture.signalsDir().resolve("$projectId.select_trace_points"))
        assertTrue(body.contains(nodeId), body)
    }

    @Test
    fun `create_tree writes nested nodes and notifies the IDE`() {
        val treeFile = fixture.projectRoot.resolve("tree.json")
        Files.writeString(
            treeFile,
            """
            {
              "file": "src/app.py",
              "line": 1,
              "content": "def alpha():",
              "name": "alpha",
              "type": "LINE",
              "children": [
                {
                  "file": "src/app.py",
                  "line": 4,
                  "content": "def beta():",
                  "name": "beta",
                  "type": "LINE"
                }
              ]
            }
            """.trimIndent(),
        )
        val r = SkillProcess.runScript(
            python,
            "create_tree.py",
            listOf("--project", fixture.projectRoot.toString(), "--tree-file", treeFile.toString()),
            fixture.projectRoot,
            fixture.appDirBase,
        )
        assertEquals(0, r.status, r.stderr + r.stdout)
        val xmlPath = java.nio.file.Paths.get(SkillProcess.parseJsonField(r.stdout, "storage_xml"))
        val xml = Files.readString(xmlPath)
        assertTrue(xml.contains("alpha"), xml)
        assertTrue(xml.contains("beta"), xml)
        val projectId = SkillProcess.projectIdFromXml(xmlPath)
        assertTrue(Files.exists(fixture.signalsDir().resolve("$projectId.request_refresh_profile")))
    }
}
