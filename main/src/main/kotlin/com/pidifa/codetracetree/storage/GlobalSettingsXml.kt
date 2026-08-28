/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import com.intellij.openapi.diagnostic.Logger
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Global highlight settings at `<appDir>/settings.xml` (lazy create on Advanced Settings save).
 *
 * Read: file if present, else caller-supplied legacy project colors, else code defaults.
 * First create migrates legacy project colors, then overlays the confirmed dialog values.
 */
object GlobalSettingsXml {
    private const val ROOT = "settings"
    private val log = Logger.getInstance(GlobalSettingsXml::class.java)

    fun file(): Path = GlobalStoragePaths.resolveSettingsFile()

    fun exists(): Boolean = Files.isRegularFile(file())

    fun read(): AdvancedSettings? {
        val path = file()
        if (!Files.isRegularFile(path)) return null
        return try {
            val xml = Files.readString(path, StandardCharsets.UTF_8)
            val root = SAXBuilder().build(StringReader(xml)).rootElement
            if (root.name != ROOT) return null
            val hlBg = root.getChild("highlightLineBackground")
            AdvancedSettings.fromXmlOrDefaults(
                hlBg?.getChildTextTrim("light"),
                hlBg?.getChildTextTrim("dark")
            )
        } catch (e: Exception) {
            log.warn("Failed to read Code Trace Tree global settings from $path", e)
            null
        }
    }

    /** `settings.xml` if present; else leftover project `<advancedSettings>`; else code defaults. Does not create the file. */
    fun resolve(legacy: AdvancedSettings?): AdvancedSettings = read() ?: legacy ?: AdvancedSettings.defaults()

    /**
     * Ensure `settings.xml` exists (seed from leftover project colors on first create), then write.
     * @return the colors that were written
     */
    fun ensureAndWrite(settings: AdvancedSettings, legacy: AdvancedSettings?): AdvancedSettings {
        val path = file()
        Files.createDirectories(path.parent)
        val toWrite = if (!Files.isRegularFile(path)) {
            migrateOnCreate(settings, legacy)
        } else {
            normalize(settings)
        }
        writeAtomic(toWrite, path)
        return toWrite
    }

    /** First settings.xml: leftover project colors as seed, then overlay dialog values. */
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

    private fun writeAtomic(settings: AdvancedSettings, path: Path) {
        val root = Element(ROOT)
        root.addContent(
            Element("highlightLineBackground").apply {
                addContent(Element("light").setText(settings.highlightLineBackgroundLight))
                addContent(Element("dark").setText(settings.highlightLineBackgroundDark))
            }
        )
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
