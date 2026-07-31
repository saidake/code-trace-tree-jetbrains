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

import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.services.TracePointService
import org.jdom.Element
import java.util.UUID

/**
 * Shared XML encode/decode for single-profile (`<traceProfile>`) and
 * multi-profile (`<traceProfiles>`) export files.
 */
object TraceProfileXml {
    const val ROOT_SINGLE = "traceProfile"
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
        root.addContent(Element("name").setText(profileName))
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
            val profileEl = Element(ROOT_SINGLE)
            profileEl.addContent(Element("name").setText(profile.name))
            profileEl.addContent(nodesElement(profile.tracePointNodes))
            profileEl.addContent(expandedElement(profile.expandedTracePointIds))
            root.addContent(profileEl)
        }
        return root
    }

    fun parseSingle(root: Element): ParsedSingle {
        if (root.name != ROOT_SINGLE) {
            throw IllegalArgumentException("Expected <$ROOT_SINGLE> root, got <${root.name}>")
        }
        val nodes = mutableListOf<TracePointService.TracePointNode>()
        val expandedIds = mutableSetOf<String>()
        val nodesEl = root.getChild("tracePointNodes")
            ?: throw IllegalArgumentException("No <tracePointNodes> element found")
        nodesEl.getChildren("tracePointNode").forEach { nodes.add(importNode(it)) }
        root.getChild("expandedTracePointIds")?.getChildren("id")?.forEach { idEl ->
            val id = idEl.textTrim
            if (id.isNotBlank()) expandedIds.add(id)
        }
        val profileName = root.getChildTextTrim("name")?.takeIf { it.isNotBlank() }
        return ParsedSingle(profileName, nodes, expandedIds)
    }

    fun parseMulti(root: Element): ParsedMulti {
        if (root.name != ROOT_MULTI) {
            throw IllegalArgumentException("Expected <$ROOT_MULTI> root, got <${root.name}>")
        }
        val profiles = mutableListOf<TracePointService.TraceProfile>()
        root.getChildren(ROOT_SINGLE).forEach { profileEl ->
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
            throw IllegalArgumentException("No <$ROOT_SINGLE> elements found")
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
            node.children.add(importNode(childEl))
        }
        return node
    }
}
