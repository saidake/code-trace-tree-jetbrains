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

import com.intellij.openapi.diagnostic.Logger
import com.pidifa.codetracetree.services.TracePointService
import org.jdom.input.SAXBuilder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.UUID
import java.util.stream.Collectors

/**
 * Resolves and persists hybrid project storage (local project id + global XML).
 *
 * Resolution on project open:
 * - Case A: match by project id → update path/updatedAt
 * - Case B: match by path (copy-on-write) → new id + new XML file
 * - Legacy: migrate `.idea/code-trace-tree-config.xml` when present
 * - Case C: create a fresh project document
 */
class ProjectStorage(private val projectBasePath: String) {
    private val log = Logger.getInstance(ProjectStorage::class.java)
    private val projectBase: Path = Paths.get(projectBasePath).toAbsolutePath().normalize()

    @Volatile
    private var boundFile: Path? = null

    @Volatile
    private var boundProjectId: String? = null

    fun resolveAndLoad(): ProjectDocument {
        Files.createDirectories(GlobalStoragePaths.resolveAppDir())

        val existingId = ProjectIdFiles.readProjectId(projectBase)

        // Case A: match by id
        if (!existingId.isNullOrBlank()) {
            val byId = findDocumentByProjectId(existingId)
            if (byId != null) {
                // Ensure IntelliJ has its own id file when we only found the VS Code one
                val ideaIdPath = ProjectIdFiles.ideaIdPath(projectBase)
                if (!Files.isRegularFile(ideaIdPath)) {
                    ProjectIdFiles.writeProjectId(projectBase, existingId)
                }
                val updated = byId.copy(
                    path = projectBase.toString(),
                    updatedAt = System.currentTimeMillis(),
                    storageFile = byId.storageFile
                )
                bind(updated)
                save(updated)
                return updated
            }
        }

        // Case B: match by path (copy-on-write)
        val byPath = findDocumentByPath(projectBase.toString())
        if (byPath != null) {
            val newId = generateProjectId()
            val newFile = allocateStorageFile(projectBase.fileName.toString())
            val copied = byPath.copy(
                projectId = newId,
                path = projectBase.toString(),
                updatedAt = System.currentTimeMillis(),
                storageFile = newFile,
                profiles = byPath.profiles.map { profile ->
                    TracePointService.TraceProfile(
                        name = profile.name.ifBlank { TracePointService.DEFAULT_PROFILE_NAME },
                        tracePointNodes = profile.tracePointNodes,
                        expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
                    )
                }.toMutableList()
            )
            ProjectIdFiles.writeProjectId(projectBase, newId)
            bind(copied)
            save(copied)
            return copied
        }

        // Legacy migration from IntelliJ PersistentStateComponent file
        val migrated = tryMigrateLegacyConfig()
        if (migrated != null) {
            bind(migrated)
            save(migrated)
            return migrated
        }

        // Case C: new project
        val newId = generateProjectId()
        val newFile = allocateStorageFile(projectBase.fileName.toString())
        val fresh = ProjectDocument(
            projectId = newId,
            path = projectBase.toString(),
            updatedAt = System.currentTimeMillis(),
            profiles = mutableListOf(
                TracePointService.TraceProfile(name = TracePointService.DEFAULT_PROFILE_NAME)
            ),
            activeProfileName = TracePointService.DEFAULT_PROFILE_NAME,
            storageFile = newFile
        )
        ProjectIdFiles.writeProjectId(projectBase, newId)
        bind(fresh)
        save(fresh)
        return fresh
    }

    fun save(
        profiles: List<TracePointService.TraceProfile>,
        activeProfileName: String,
        descriptionAreaOpened: Boolean,
        highlightingEnabled: Boolean
    ) {
        val file = boundFile ?: return
        val projectId = boundProjectId ?: return
        val doc = ProjectDocument(
            projectId = projectId,
            path = projectBase.toString(),
            updatedAt = System.currentTimeMillis(),
            profiles = profiles.map { profile ->
                TracePointService.TraceProfile(
                    name = profile.name.ifBlank { TracePointService.DEFAULT_PROFILE_NAME },
                    tracePointNodes = profile.tracePointNodes,
                    expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
                )
            }.toMutableList(),
            activeProfileName = activeProfileName,
            descriptionAreaOpened = descriptionAreaOpened,
            highlightingEnabled = highlightingEnabled,
            storageFile = file
        )
        save(doc)
    }

    private fun save(doc: ProjectDocument) {
        val file = doc.storageFile ?: boundFile ?: return
        try {
            ProjectDataXml.writeAtomic(doc, file)
            bind(doc.copy(storageFile = file))
        } catch (e: Exception) {
            log.warn("Failed to save Code Trace Tree project data to $file", e)
        }
    }

    private fun bind(doc: ProjectDocument) {
        boundFile = doc.storageFile
        boundProjectId = doc.projectId
    }

    private fun generateProjectId(): String = UUID.randomUUID().toString()

    private fun allocateStorageFile(folderName: String): Path {
        val dir = GlobalStoragePaths.resolveAppDir()
        Files.createDirectories(dir)
        val safeName = folderName.ifBlank { "project" }.replace(Regex("""[<>:"/\\|?*]"""), "_")
        var candidate = dir.resolve("$safeName.xml")
        var index = 1
        while (Files.exists(candidate)) {
            candidate = dir.resolve("$safeName-$index.xml")
            index++
        }
        return candidate
    }

    private fun listProjectXmlFiles(): List<Path> {
        val dir = GlobalStoragePaths.resolveAppDir()
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
                .collect(Collectors.toList())
        }
    }

    private fun findDocumentByProjectId(projectId: String): ProjectDocument? {
        for (file in listProjectXmlFiles()) {
            try {
                val doc = ProjectDataXml.parseFile(file)
                if (doc.projectId == projectId) {
                    return doc.copy(storageFile = file)
                }
            } catch (e: Exception) {
                log.debug("Skipping unreadable storage file $file", e)
            }
        }
        return null
    }

    private fun findDocumentByPath(absolutePath: String): ProjectDocument? {
        val normalized = normalizePath(absolutePath)
        for (file in listProjectXmlFiles()) {
            try {
                val doc = ProjectDataXml.parseFile(file)
                if (normalizePath(doc.path) == normalized) {
                    return doc.copy(storageFile = file)
                }
            } catch (e: Exception) {
                log.debug("Skipping unreadable storage file $file", e)
            }
        }
        return null
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) return ""
        return try {
            val normalized = Paths.get(path).toAbsolutePath().normalize().toString()
            if (isWindows()) normalized.lowercase(Locale.ROOT) else normalized
        } catch (_: Exception) {
            if (isWindows()) path.lowercase(Locale.ROOT) else path
        }
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase(Locale.ROOT).contains("win")

    /**
     * Migrates legacy `.idea/code-trace-tree-config.xml` (PersistentStateComponent) into
     * global storage and writes a new project id.
     */
    private fun tryMigrateLegacyConfig(): ProjectDocument? {
        val legacyFile = projectBase.resolve(".idea").resolve("code-trace-tree-config.xml")
        if (!Files.isRegularFile(legacyFile)) return null
        return try {
            val root = SAXBuilder().build(legacyFile.toFile()).rootElement
            val component = when {
                root.name == "component" -> root
                else -> root.getChildren("component")
                    .firstOrNull { it.getAttributeValue("name") == "TracePointService" }
                    ?: root
            }

            val profiles = mutableListOf<TracePointService.TraceProfile>()
            component.getChild("traceProfiles")?.getChildren("traceProfile")?.forEach { profileEl ->
                val name = profileEl.getChildTextTrim("name")?.takeIf { it.isNotBlank() }
                    ?: TracePointService.DEFAULT_PROFILE_NAME
                val nodes = mutableListOf<TracePointService.TracePointNode>()
                profileEl.getChild("tracePointNodes")?.getChildren("tracePointNode")?.forEach {
                    nodes.add(parseLegacyNode(it))
                }
                val expanded = mutableSetOf<String>()
                profileEl.getChild("expandedTracePointIds")?.getChildren("id")?.forEach {
                    val id = it.textTrim
                    if (id.isNotBlank()) expanded.add(id)
                }
                profiles.add(
                    TracePointService.TraceProfile(
                        name = name,
                        tracePointNodes = nodes,
                        expandedTracePointIds = expanded
                    )
                )
            }

            // Pre-profile legacy fields
            if (profiles.isEmpty()) {
                val nodes = mutableListOf<TracePointService.TracePointNode>()
                component.getChild("tracePointNodes")?.getChildren("tracePointNode")?.forEach {
                    nodes.add(parseLegacyNode(it))
                }
                val expanded = mutableSetOf<String>()
                component.getChild("expandedTracePointIds")?.getChildren("id")?.forEach {
                    val id = it.textTrim
                    if (id.isNotBlank()) expanded.add(id)
                }
                profiles.add(
                    TracePointService.TraceProfile(
                        name = TracePointService.DEFAULT_PROFILE_NAME,
                        tracePointNodes = nodes,
                        expandedTracePointIds = expanded
                    )
                )
            }

            val active = component.getChildTextTrim("activeProfileName")
                ?.takeIf { it.isNotBlank() }
                ?: profiles.first().name
            val descriptionOpened =
                component.getChildTextTrim("descriptionAreaOpened")?.toBooleanStrictOrNull() ?: false
            val highlighting =
                component.getChildTextTrim("highlightingEnabled")?.toBooleanStrictOrNull() ?: true

            val newId = generateProjectId()
            val newFile = allocateStorageFile(projectBase.fileName.toString())
            ProjectIdFiles.writeProjectId(projectBase, newId)

            ProjectDocument(
                projectId = newId,
                path = projectBase.toString(),
                updatedAt = System.currentTimeMillis(),
                profiles = profiles,
                activeProfileName = active,
                descriptionAreaOpened = descriptionOpened,
                highlightingEnabled = highlighting,
                storageFile = newFile
            )
        } catch (e: Exception) {
            log.warn("Failed to migrate legacy code-trace-tree-config.xml", e)
            null
        }
    }

    private fun parseLegacyNode(nodeEl: org.jdom.Element): TracePointService.TracePointNode {
        val id = nodeEl.getChildTextTrim("id") ?: UUID.randomUUID().toString()
        val parentId = nodeEl.getChildTextTrim("parentId")?.takeIf { it.isNotBlank() }
        // XMLB may flatten TracePoint with surroundWithTag=false — fields can be direct children
        val tpEl = nodeEl.getChild("tracePoint")
        val name: String
        val fileName: String
        val filePath: String
        val lineNumber: Int
        val lineContent: String
        val totalOccurrences: Int
        val occurrenceIndex: Int
        val description: String

        if (tpEl != null) {
            name = tpEl.getChildTextTrim("name") ?: ""
            fileName = tpEl.getChildTextTrim("fileName") ?: ""
            filePath = tpEl.getChildTextTrim("filePath") ?: ""
            lineNumber = tpEl.getChildTextTrim("lineNumber")?.toIntOrNull() ?: -1
            lineContent = tpEl.getChildTextTrim("lineContent") ?: ""
            totalOccurrences = tpEl.getChildTextTrim("totalOccurrences")?.toIntOrNull() ?: 1
            occurrenceIndex = tpEl.getChildTextTrim("occurrenceIndex")?.toIntOrNull() ?: 1
            description = tpEl.getChildTextTrim("description") ?: ""
        } else {
            name = nodeEl.getChildTextTrim("name") ?: ""
            fileName = nodeEl.getChildTextTrim("fileName") ?: ""
            filePath = nodeEl.getChildTextTrim("filePath") ?: ""
            lineNumber = nodeEl.getChildTextTrim("lineNumber")?.toIntOrNull() ?: -1
            lineContent = nodeEl.getChildTextTrim("lineContent") ?: ""
            totalOccurrences = nodeEl.getChildTextTrim("totalOccurrences")?.toIntOrNull() ?: 1
            occurrenceIndex = nodeEl.getChildTextTrim("occurrenceIndex")?.toIntOrNull() ?: 1
            description = nodeEl.getChildTextTrim("description") ?: ""
        }

        val node = TracePointService.TracePointNode(
            id,
            TracePointService.TracePoint(
                name = name,
                fileName = fileName,
                filePath = filePath,
                lineNumber = lineNumber,
                lineContent = lineContent,
                isValid = true,
                totalOccurrences = totalOccurrences,
                occurrenceIndex = occurrenceIndex,
                description = description
            ),
            parentId
        )
        nodeEl.getChild("children")?.getChildren("tracePointNode")?.forEach { childEl ->
            val child = parseLegacyNode(childEl)
            if (child.parentId == null) child.parentId = id
            node.children.add(child)
        }
        return node
    }
}
