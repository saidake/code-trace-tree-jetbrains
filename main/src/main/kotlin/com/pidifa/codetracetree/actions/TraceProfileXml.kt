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
package com.pidifa.codetracetree.actions

import com.pidifa.codetracetree.services.TracePointService
import org.jdom.Element
import java.util.UUID

/**
 * Shared XML encode/decode for single-profile (`<tracePointState>`) and
 * multi-profile (`<traceProfiles>`) export files.
 */
object TraceProfileXml {
    const val ROOT_SINGLE = "tracePointState"
    const val ROOT_MULTI = "traceProfiles"

    data class ParsedSingle(
        val profileName: String?,
        val nodes: MutableList<TracePointService.TracePointNode>,
        val expandedIds: MutableSet<String>
    )

    data class ParsedMulti(
        val activeProfileName: String?,
        val profiles: List<TracePointService.TraceProfile>
    )

    fun exportSingleProfile(
        profileName: String,
        nodes: List<TracePointService.TracePointNode>,
        expandedIds: Set<String>
    ): Element {
        val root = Element(ROOT_SINGLE)
        root.addContent(Element("profileName").setText(profileName))
        root.addContent(nodesElement(nodes))
        root.addContent(expandedElement(expandedIds))
        return root
    }

    fun exportAllProfiles(
        profiles: List<TracePointService.TraceProfile>,
        activeProfileName: String
    ): Element {
        val root = Element(ROOT_MULTI)
        root.addContent(Element("activeProfileName").setText(activeProfileName))
        profiles.forEach { profile ->
            val profileEl = Element("traceProfile")
            profileEl.addContent(Element("name").setText(profile.name))
            profileEl.addContent(nodesElement(profile.tracePointNodes))
            profileEl.addContent(expandedElement(profile.expandedTracePointIds))
            root.addContent(profileEl)
        }
        return root
    }

    fun parseSingle(root: Element): ParsedSingle {
        val nodes = mutableListOf<TracePointService.TracePointNode>()
        val expandedIds = mutableSetOf<String>()
        val nodesEl = root.getChild("tracePointNodes")
            ?: throw IllegalArgumentException("No <tracePointNodes> element found")
        nodesEl.getChildren("tracePointNode").forEach { nodes.add(importNode(it)) }
        root.getChild("expandedTracePointIds")?.getChildren("id")?.forEach { idEl ->
            val id = idEl.textTrim
            if (id.isNotBlank()) expandedIds.add(id)
        }
        val profileName = root.getChildTextTrim("profileName")?.takeIf { it.isNotBlank() }
        return ParsedSingle(profileName, nodes, expandedIds)
    }

    fun parseMulti(root: Element): ParsedMulti {
        val profiles = mutableListOf<TracePointService.TraceProfile>()
        root.getChildren("traceProfile").forEach { profileEl ->
            val name = profileEl.getChildTextTrim("name")?.takeIf { it.isNotBlank() }
                ?: TracePointService.DEFAULT_PROFILE_NAME
            val nodes = mutableListOf<TracePointService.TracePointNode>()
            val expandedIds = mutableSetOf<String>()
            profileEl.getChild("tracePointNodes")?.getChildren("tracePointNode")?.forEach {
                nodes.add(importNode(it))
            }
            profileEl.getChild("expandedTracePointIds")?.getChildren("id")?.forEach { idEl ->
                val id = idEl.textTrim
                if (id.isNotBlank()) expandedIds.add(id)
            }
            profiles.add(
                TracePointService.TraceProfile(
                    name = name,
                    tracePointNodes = nodes,
                    expandedTracePointIds = expandedIds
                )
            )
        }
        if (profiles.isEmpty()) {
            throw IllegalArgumentException("No <traceProfile> elements found")
        }
        val active = root.getChildTextTrim("activeProfileName")?.takeIf { it.isNotBlank() }
        return ParsedMulti(active, profiles)
    }

    private fun nodesElement(nodes: List<TracePointService.TracePointNode>): Element {
        val el = Element("tracePointNodes")
        nodes.forEach { exportNode(it, el) }
        return el
    }

    private fun expandedElement(ids: Set<String>): Element {
        val el = Element("expandedTracePointIds")
        ids.forEach { id -> el.addContent(Element("id").setText(id)) }
        return el
    }

    private fun exportNode(node: TracePointService.TracePointNode, parentEl: Element) {
        val nodeEl = Element("tracePointNode")
        nodeEl.addContent(Element("id").setText(node.id))
        nodeEl.addContent(Element("parentId").setText(node.parentId ?: ""))

        val tracePointEl = Element("tracePoint").apply {
            addContent(Element("name").setText(node.tracePoint.name))
            addContent(Element("fileName").setText(node.tracePoint.fileName))
            addContent(Element("filePath").setText(node.tracePoint.filePath))
            addContent(Element("lineNumber").setText(node.tracePoint.lineNumber.toString()))
            addContent(Element("projectPath").setText(node.tracePoint.projectPath))
            addContent(Element("lineContent").setText(node.tracePoint.lineContent ?: ""))
            addContent(Element("isValid").setText(node.tracePoint.isValid.toString()))
            addContent(Element("totalOccurrences").setText(node.tracePoint.totalOccurrences.toString()))
            addContent(Element("occurrenceIndex").setText(node.tracePoint.occurrenceIndex.toString()))
            addContent(Element("description").setText(node.tracePoint.description ?: ""))
        }
        nodeEl.addContent(tracePointEl)

        if (node.children.isNotEmpty()) {
            val childrenEl = Element("children")
            node.children.forEach { exportNode(it, childrenEl) }
            nodeEl.addContent(childrenEl)
        }

        parentEl.addContent(nodeEl)
    }

    private fun importNode(nodeEl: Element): TracePointService.TracePointNode {
        val id = nodeEl.getChildTextTrim("id") ?: UUID.randomUUID().toString()
        val parentId = nodeEl.getChildTextTrim("parentId")?.takeIf { it.isNotBlank() }
        val tpEl = nodeEl.getChild("tracePoint")
            ?: throw IllegalArgumentException("Missing <tracePoint> element in node $id")

        val tp = TracePointService.TracePoint(
            name = tpEl.getChildTextTrim("name") ?: "",
            fileName = tpEl.getChildTextTrim("fileName") ?: "",
            filePath = tpEl.getChildTextTrim("filePath") ?: "",
            lineNumber = tpEl.getChildTextTrim("lineNumber")?.toIntOrNull() ?: -1,
            projectPath = tpEl.getChildTextTrim("projectPath") ?: "",
            lineContent = tpEl.getChildTextTrim("lineContent") ?: "",
            isValid = tpEl.getChildTextTrim("isValid")?.toBoolean() ?: true,
            totalOccurrences = tpEl.getChildTextTrim("totalOccurrences")?.toIntOrNull() ?: 1,
            occurrenceIndex = tpEl.getChildTextTrim("occurrenceIndex")?.toIntOrNull() ?: 1,
            description = tpEl.getChildTextTrim("description") ?: ""
        )

        val node = TracePointService.TracePointNode(id, tp, parentId)
        nodeEl.getChild("children")?.getChildren("tracePointNode")?.forEach { childEl ->
            node.children.add(importNode(childEl))
        }
        return node
    }
}
