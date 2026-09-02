/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import com.intellij.openapi.diagnostic.Logger
import com.pidifa.codetracetree.skill.AgentSkillNoticeStatus
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

data class GlobalSettingsFile(
    val highlightLineBackgroundLight: String,
    val highlightLineBackgroundDark: String,
    val agentSkillVersion: String? = null,
    val agentSkillNoticeStatus: AgentSkillNoticeStatus? = null,
) {
    fun colors(): AdvancedSettings = AdvancedSettings(
        highlightLineBackgroundLight = highlightLineBackgroundLight,
        highlightLineBackgroundDark = highlightLineBackgroundDark,
    )
}

/**
 * Global settings at `<appDir>/settings.xml` (highlight colors + agent-skill notice).
 *
 * Colors: file if present, else leftover project colors, else code defaults.
 * First Advanced Settings save migrates leftover project colors.
 * Agent-skill notice may also create the file (seeding colors).
 */
object GlobalSettingsXml {
    private const val ROOT = "settings"
    private val log = Logger.getInstance(GlobalSettingsXml::class.java)

    fun file(): Path = GlobalStoragePaths.resolveSettingsFile()

    fun exists(): Boolean = Files.isRegularFile(file())

    fun readFile(): GlobalSettingsFile? {
        val path = file()
        if (!Files.isRegularFile(path)) return null
        return try {
            parse(Files.readString(path, StandardCharsets.UTF_8))
        } catch (e: Exception) {
            log.warn("Failed to read Code Trace Tree global settings from $path", e)
            null
        }
    }

    fun parse(xml: String): GlobalSettingsFile? {
        val root = SAXBuilder().build(StringReader(xml)).rootElement
        if (root.name != ROOT) return null
        val hlBg = root.getChild("highlightLineBackground")
        val colors = AdvancedSettings.fromXmlOrDefaults(
            hlBg?.getChildTextTrim("light"),
            hlBg?.getChildTextTrim("dark")
        )
        val skill = root.getChild("agentSkill")
        val version = skill?.getChildTextTrim("version")?.takeIf { it.isNotBlank() }
        val status = when (skill?.getChildTextTrim("noticeStatus")) {
            "dismissed" -> AgentSkillNoticeStatus.DISMISSED
            "opened", "installed" -> AgentSkillNoticeStatus.OPENED
            else -> null
        }
        return GlobalSettingsFile(
            highlightLineBackgroundLight = colors.highlightLineBackgroundLight,
            highlightLineBackgroundDark = colors.highlightLineBackgroundDark,
            agentSkillVersion = version,
            agentSkillNoticeStatus = status,
        )
    }

    fun read(): AdvancedSettings? = readFile()?.colors()

    /** `settings.xml` if present; else leftover project `<advancedSettings>`; else code defaults. Does not create the file. */
    fun resolve(legacy: AdvancedSettings?): AdvancedSettings = read() ?: legacy ?: AdvancedSettings.defaults()

    /**
     * Ensure `settings.xml` exists (seed from leftover project colors on first create), then write.
     * Preserves agent-skill notice fields when the file already exists.
     */
    fun ensureAndWrite(settings: AdvancedSettings, legacy: AdvancedSettings?): AdvancedSettings {
        val existing = readFile()
        val colors = if (existing == null) migrateOnCreate(settings, legacy) else normalize(settings)
        writeAtomic(
            GlobalSettingsFile(
                highlightLineBackgroundLight = colors.highlightLineBackgroundLight,
                highlightLineBackgroundDark = colors.highlightLineBackgroundDark,
                agentSkillVersion = existing?.agentSkillVersion,
                agentSkillNoticeStatus = existing?.agentSkillNoticeStatus,
            )
        )
        return colors
    }

    fun upsertAgentSkillNotice(
        version: String,
        status: AgentSkillNoticeStatus,
        colorSeed: AdvancedSettings,
    ) {
        val existing = readFile()
        val colors = existing?.colors() ?: normalize(colorSeed)
        writeAtomic(
            GlobalSettingsFile(
                highlightLineBackgroundLight = colors.highlightLineBackgroundLight,
                highlightLineBackgroundDark = colors.highlightLineBackgroundDark,
                agentSkillVersion = version,
                agentSkillNoticeStatus = status,
            )
        )
    }

    private fun migrateOnCreate(dialog: AdvancedSettings, legacy: AdvancedSettings?): AdvancedSettings {
        val seeded = legacy ?: AdvancedSettings.defaults()
        return AdvancedSettings(
            highlightLineBackgroundLight = AdvancedSettings.normalizeHex(dialog.highlightLineBackgroundLight)
                ?: seeded.highlightLineBackgroundLight,
            highlightLineBackgroundDark = AdvancedSettings.normalizeHex(dialog.highlightLineBackgroundDark)
                ?: seeded.highlightLineBackgroundDark
        )
    }

    private fun normalize(settings: AdvancedSettings): AdvancedSettings =
        AdvancedSettings(
            highlightLineBackgroundLight = AdvancedSettings.normalizeHex(settings.highlightLineBackgroundLight)
                ?: AdvancedSettings.DEFAULT_HIGHLIGHT_LIGHT,
            highlightLineBackgroundDark = AdvancedSettings.normalizeHex(settings.highlightLineBackgroundDark)
                ?: AdvancedSettings.DEFAULT_HIGHLIGHT_DARK
        )

    private fun writeAtomic(doc: GlobalSettingsFile) {
        val path = file()
        Files.createDirectories(path.parent)
        val root = Element(ROOT)
        root.addContent(
            Element("highlightLineBackground").apply {
                addContent(Element("light").setText(doc.highlightLineBackgroundLight))
                addContent(Element("dark").setText(doc.highlightLineBackgroundDark))
            }
        )
        if (!doc.agentSkillVersion.isNullOrBlank() || doc.agentSkillNoticeStatus != null) {
            root.addContent(
                Element("agentSkill").apply {
                    if (!doc.agentSkillVersion.isNullOrBlank()) {
                        addContent(Element("version").setText(doc.agentSkillVersion))
                    }
                    if (doc.agentSkillNoticeStatus != null) {
                        addContent(
                            Element("noticeStatus").setText(
                                doc.agentSkillNoticeStatus.name.lowercase()
                            )
                        )
                    }
                }
            )
        }
        val outputter = XMLOutputter(Format.getPrettyFormat().setEncoding("UTF-8"))
        val xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + outputter.outputString(root)
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        Files.writeString(tmp, xml, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
