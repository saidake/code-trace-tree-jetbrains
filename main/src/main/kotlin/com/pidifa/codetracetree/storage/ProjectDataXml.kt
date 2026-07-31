/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pidifa.codetracetree.storage

import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.services.TracePointService
import org.jdom.Element
import org.jdom.input.SAXBuilder
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.StringReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Shared project document stored under global central storage (`data.xml` design).
 */
data class ProjectDocument(
    val version: Int = CURRENT_VERSION,
    val projectId: String,
    val path: String,
    val updatedAt: Long,
    val profiles: MutableList<TracePointService.TraceProfile>,
    val activeProfileName: String,
    val descriptionAreaOpened: Boolean = false,
    val highlightingEnabled: Boolean = true,
    /** When true, creating a trace point prompts for a name; when false, creates with an empty name. */
    val namePromptEnabled: Boolean = true,
    /** Absolute path of the XML file this document was loaded from / should be saved to. */
    val storageFile: Path? = null
) {
    companion object {
        const val CURRENT_VERSION = 4
    }
}

object ProjectDataXml {
    private const val ROOT = "project"

    fun parse(xml: String, storageFile: Path? = null): ProjectDocument {
        val root = SAXBuilder().build(StringReader(xml)).rootElement
        return parseElement(root, storageFile)
    }

    fun parseFile(file: Path): ProjectDocument {
        val xml = Files.readString(file, StandardCharsets.UTF_8)
        return parse(xml, file)
    }

    fun parseElement(root: Element, storageFile: Path? = null): ProjectDocument {
        if (root.name != ROOT) {
            throw IllegalArgumentException("Expected <$ROOT> root, got <${root.name}>")
        }
        val version = root.getAttributeValue("version")?.toIntOrNull() ?: ProjectDocument.CURRENT_VERSION
        val projectId = root.getChildTextTrim("projectId")
            ?: throw IllegalArgumentException("Missing <projectId>")
        val path = root.getChildTextTrim("path") ?: ""
        val updatedAt = root.getChildTextTrim("updatedAt")?.toLongOrNull() ?: 0L

        val profiles = mutableListOf<TracePointService.TraceProfile>()
        root.getChild("traceProfiles")?.getChildren("traceProfile")?.forEach { profileEl ->
            profiles.add(parseProfile(profileEl))
        }

        val activeProfileName = root.getChildTextTrim("activeProfileName")
            ?.takeIf { it.isNotBlank() }
            ?: profiles.firstOrNull()?.name
            ?: TracePointService.DEFAULT_PROFILE_NAME

        val descriptionAreaOpened =
            root.getChildTextTrim("descriptionAreaOpened")?.toBooleanStrictOrNull() ?: false
        val highlightingEnabled =
            root.getChildTextTrim("highlightingEnabled")?.toBooleanStrictOrNull() ?: true
        val namePromptEnabled =
            root.getChildTextTrim("namePromptEnabled")?.toBooleanStrictOrNull() ?: true

        return ProjectDocument(
            version = version,
            projectId = projectId,
            path = path,
            updatedAt = updatedAt,
            profiles = if (profiles.isEmpty()) {
                mutableListOf(TracePointService.TraceProfile(name = TracePointService.DEFAULT_PROFILE_NAME))
            } else {
                profiles
            },
            activeProfileName = activeProfileName,
            descriptionAreaOpened = descriptionAreaOpened,
            highlightingEnabled = highlightingEnabled,
            namePromptEnabled = namePromptEnabled,
            storageFile = storageFile
        )
    }

    fun toElement(doc: ProjectDocument): Element {
        val root = Element(ROOT)
        root.setAttribute("version", doc.version.toString())
        root.addContent(Element("projectId").setText(doc.projectId))
        root.addContent(Element("path").setText(doc.path))
        root.addContent(Element("updatedAt").setText(doc.updatedAt.toString()))
        root.addContent(Element("activeProfileName").setText(doc.activeProfileName))
        root.addContent(Element("highlightingEnabled").setText(doc.highlightingEnabled.toString()))
        root.addContent(Element("namePromptEnabled").setText(doc.namePromptEnabled.toString()))

        val profilesEl = Element("traceProfiles")
        for (profile in doc.profiles) {
            profilesEl.addContent(profileElement(profile))
        }
        root.addContent(profilesEl)
        root.addContent(Element("descriptionAreaOpened").setText(doc.descriptionAreaOpened.toString()))
        return root
    }

    fun toXmlString(doc: ProjectDocument): String {
        val outputter = XMLOutputter(Format.getPrettyFormat().setEncoding("UTF-8"))
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + outputter.outputString(toElement(doc))
    }

    fun writeAtomic(doc: ProjectDocument, file: Path) {
        Files.createDirectories(file.parent)
        val xml = toXmlString(doc)
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, xml, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun parseProfile(profileEl: Element): TracePointService.TraceProfile {
        // Always persist/read a name — including the default "main" profile.
        val name = profileEl.getChildTextTrim("name")?.takeIf { it.isNotBlank() }
            ?: TracePointService.DEFAULT_PROFILE_NAME
        val nodes = mutableListOf<TracePointService.TracePointNode>()
        profileEl.getChild("tracePointNodes")?.getChildren("tracePointNode")?.forEach {
            nodes.add(parseNode(it))
        }
        val expandedIds = mutableSetOf<String>()
        profileEl.getChild("expandedTracePointIds")?.getChildren("id")?.forEach { idEl ->
            val id = idEl.textTrim
            if (id.isNotBlank()) expandedIds.add(id)
        }
        return TracePointService.TraceProfile(
            name = name,
            tracePointNodes = nodes,
            expandedTracePointIds = expandedIds
        )
    }

    private fun profileElement(profile: TracePointService.TraceProfile): Element {
        val el = Element("traceProfile")
        el.addContent(Element("name").setText(profile.name.ifBlank { TracePointService.DEFAULT_PROFILE_NAME }))
        val nodesEl = Element("tracePointNodes")
        profile.tracePointNodes.forEach { nodesEl.addContent(nodeElement(it, parentId = null)) }
        el.addContent(nodesEl)
        val expandedEl = Element("expandedTracePointIds")
        profile.expandedTracePointIds.forEach { id ->
            expandedEl.addContent(Element("id").setText(id))
        }
        el.addContent(expandedEl)
        return el
    }

    private fun parseNode(nodeEl: Element): TracePointService.TracePointNode {
        val id = nodeEl.getChildTextTrim("id") ?: UUID.randomUUID().toString()
        val parentId = nodeEl.getChildTextTrim("parentId")?.takeIf { it.isNotBlank() }
        val tpEl = nodeEl.getChild("tracePoint")
            ?: throw IllegalArgumentException("Missing <tracePoint> in node $id")

        val kind = TraceType.fromXml(tpEl.getChildTextTrim("traceType"))
        val tp = when (kind) {
            TraceType.LINE -> TracePointService.TracePoint(
                traceName = tpEl.getChildTextTrim("traceName") ?: "",
                traceType = TraceType.LINE,
                baseName = tpEl.getChildTextTrim("baseName") ?: "",
                tracePath = tpEl.getChildTextTrim("tracePath") ?: "",
                lineNumber = tpEl.getChildTextTrim("lineNumber")?.toIntOrNull() ?: -1,
                lineContent = tpEl.getChildTextTrim("lineContent") ?: "",
                isValid = true,
                totalOccurrences = tpEl.getChildTextTrim("totalOccurrences")?.toIntOrNull() ?: 1,
                occurrenceIndex = tpEl.getChildTextTrim("occurrenceIndex")?.toIntOrNull() ?: 1,
                description = tpEl.getChildTextTrim("description") ?: ""
            )
            TraceType.FILE, TraceType.DIRECTORY -> TracePointService.TracePoint(
                traceName = tpEl.getChildTextTrim("traceName") ?: "",
                traceType = kind,
                baseName = tpEl.getChildTextTrim("baseName") ?: "",
                tracePath = tpEl.getChildTextTrim("tracePath") ?: "",
                lineNumber = 0,
                lineContent = null,
                isValid = true,
                totalOccurrences = 0,
                occurrenceIndex = 0,
                description = tpEl.getChildTextTrim("description") ?: ""
            )
        }

        val node = TracePointService.TracePointNode(id, tp, parentId)
        nodeEl.getChild("children")?.getChildren("tracePointNode")?.forEach { childEl ->
            val child = parseNode(childEl)
            if (child.parentId == null) child.parentId = id
            node.children.add(child)
        }
        return node
    }

    /** Every node includes parentId (empty for roots). */
    private fun nodeElement(node: TracePointService.TracePointNode, parentId: String?): Element {
        val nodeEl = Element("tracePointNode")
        nodeEl.addContent(Element("id").setText(node.id))
        nodeEl.addContent(Element("parentId").setText(parentId ?: ""))

        val tp = node.tracePoint
        nodeEl.addContent(
            Element("tracePoint").apply {
                addContent(Element("traceName").setText(tp.traceName))
                addContent(Element("traceType").setText(tp.traceType.name))
                addContent(Element("baseName").setText(tp.baseName))
                addContent(Element("tracePath").setText(tp.tracePath))
                if (tp.traceType == TraceType.LINE) {
                    addContent(Element("lineNumber").setText(tp.lineNumber.toString()))
                    addContent(Element("lineContent").setText(tp.lineContent ?: ""))
                    addContent(Element("totalOccurrences").setText(tp.totalOccurrences.toString()))
                    addContent(Element("occurrenceIndex").setText(tp.occurrenceIndex.toString()))
                }
                if (!tp.description.isNullOrEmpty()) {
                    addContent(Element("description").setText(tp.description))
                }
            }
        )

        if (node.children.isNotEmpty()) {
            val childrenEl = Element("children")
            node.children.forEach { child ->
                childrenEl.addContent(nodeElement(child, parentId = node.id))
            }
            nodeEl.addContent(childrenEl)
        }
        return nodeEl
    }
}
