/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.skill

import com.pidifa.codetracetree.storage.GlobalSettingsXml
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class AgentSkillTest {
    @Test
    fun parseSkillVersionFromFrontmatter() {
        val md = "---\nname: code-trace-tree\nmetadata:\n  version: \"1\"\ndescription: test\n---\n\n# Hi\n"
        assertEquals("1", AgentSkill.parseSkillVersion(md))
        assertEquals(null, AgentSkill.parseSkillVersion("---\nversion: 1\n---\n"))
    }

    @Test
    fun compareSkillVersionsTreatsMissingAndNonIntegerAsZero() {
        assertTrue(AgentSkill.compareSkillVersions(null, "1") < 0)
        assertTrue(AgentSkill.compareSkillVersions("1.3.5", "1") < 0)
        assertTrue(AgentSkill.compareSkillVersions("", "1") < 0)
        assertEquals(0, AgentSkill.compareSkillVersions("1", "1"))
        assertTrue(AgentSkill.compareSkillVersions("2", "1") > 0)
    }

    @Test
    fun scanDetectsHomeMarkerAndOutdatedSkill(@TempDir home: Path) {
        Files.createDirectories(home.resolve(".cursor/skills/code-trace-tree"))
        Files.writeString(
            home.resolve(".cursor/skills/code-trace-tree/SKILL.md"),
            "---\nname: code-trace-tree\nmetadata:\n  version: \"1\"\n---\n",
        )
        val statuses = AgentSkill.scanAgentStatuses("2", home)
        val cursor = statuses.first { it.id == "cursor" }
        val claude = statuses.first { it.id == "claude-code" }
        assertTrue(cursor.detected)
        assertEquals(AgentSkillState.OUTDATED, cursor.state)
        assertEquals("1", cursor.installedVersion)
        assertFalse(claude.detected)
        assertEquals(AgentSkillState.MISSING, claude.state)
        assertTrue(AgentSkill.shouldOfferSkillNotice("2", statuses, null))
        assertTrue(AgentSkill.shouldOfferSkillNotice("2", statuses, "1"))
        assertFalse(AgentSkill.shouldOfferSkillNotice("2", statuses, "2"))
    }

    @Test
    fun copySkillDirReplacesExisting(@TempDir root: Path) {
        val src = root.resolve("src")
        val dest = root.resolve("dest/code-trace-tree")
        Files.createDirectories(src.resolve("scripts"))
        Files.writeString(src.resolve("SKILL.md"), "---\nmetadata:\n  version: \"1\"\n---\n")
        Files.writeString(src.resolve("scripts/trace_tree.py"), "print(1)\n")
        Files.createDirectories(dest)
        Files.writeString(dest.resolve("old.txt"), "stale")
        AgentSkill.copySkillDir(src, dest)
        assertFalse(Files.exists(dest.resolve("old.txt")))
        assertTrue(Files.isRegularFile(dest.resolve("SKILL.md")))
        assertTrue(Files.isRegularFile(dest.resolve("scripts/trace_tree.py")))
    }

    @Test
    fun removeSkillForAgentsDeletesInstalledFolder(@TempDir home: Path) {
        val dest = home.resolve(".cursor/skills/code-trace-tree")
        Files.createDirectories(dest)
        Files.writeString(dest.resolve("SKILL.md"), "---\nmetadata:\n  version: \"1\"\n---\n")
        val removed = AgentSkill.removeSkillForAgents(listOf("cursor", "claude-code"), home)
        assertEquals(1, removed.size)
        assertEquals("cursor", removed[0].first)
        assertFalse(Files.exists(dest))
    }

    @Test
    fun bundledSkillMdIsOnClasspath() {
        val md = BundledSkill.skillMdText()
        assertEquals("1", AgentSkill.parseSkillVersion(md))
    }

    @Test
    fun parseGlobalSettingsXmlRoundTrip() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <settings>
              <highlightLineBackground>
                <light>#FFFFC8</light>
                <dark>#236C60</dark>
              </highlightLineBackground>
              <agentSkill>
                <version>1</version>
                <noticeStatus>dismissed</noticeStatus>
              </agentSkill>
            </settings>
        """.trimIndent()
        val parsed = GlobalSettingsXml.parse(xml)!!
        assertEquals("#FFFFC8", parsed.highlightLineBackgroundLight)
        assertEquals("#236C60", parsed.highlightLineBackgroundDark)
        assertEquals("1", parsed.agentSkillVersion)
        assertEquals(AgentSkillNoticeStatus.DISMISSED, parsed.agentSkillNoticeStatus)
        val openedXml = xml.replace("<noticeStatus>dismissed</noticeStatus>", "<noticeStatus>opened</noticeStatus>")
        assertEquals(AgentSkillNoticeStatus.OPENED, GlobalSettingsXml.parse(openedXml)!!.agentSkillNoticeStatus)
        val legacyInstalled = xml.replace("<noticeStatus>dismissed</noticeStatus>", "<noticeStatus>installed</noticeStatus>")
        assertEquals(AgentSkillNoticeStatus.OPENED, GlobalSettingsXml.parse(legacyInstalled)!!.agentSkillNoticeStatus)
    }
}
