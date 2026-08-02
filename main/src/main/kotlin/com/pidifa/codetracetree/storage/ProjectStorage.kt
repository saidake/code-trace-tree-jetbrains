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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.UUID
import java.util.stream.Collectors

/**
 * Resolves and persists hybrid project storage (local project id + global XML).
 *
 * Global file naming: `<projectId>.xml`.
 * Legacy `FolderName.xml` files (previous releases) are still found by scanning
 * `<projectId>` inside XML and best-effort renamed to the canonical name.
 *
 * Resolution on project open:
 * - Case A: match by project id → update path/updatedAt
 * - Case B: match by path (copy-on-write) → new id + new XML file
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
            val newFile = allocateStorageFile(newId)
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

        // Case C: new project
        val newId = generateProjectId()
        val newFile = allocateStorageFile(newId)
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

    fun boundStorageFile(): Path? = boundFile

    fun boundProjectId(): String? = boundProjectId

    /**
     * Re-reads the currently bound global XML without re-running project resolution.
     * Returns null when unbound or the file is missing/unreadable.
     */
    fun reloadBoundDocument(): ProjectDocument? {
        val file = boundFile ?: return null
        if (!Files.isRegularFile(file)) return null
        return try {
            val doc = ProjectDataXml.parseFile(file)
            val rebound = doc.copy(storageFile = file)
            bind(rebound)
            rebound
        } catch (e: Exception) {
            log.warn("Failed to reload Code Trace Tree project data from $file", e)
            null
        }
    }

    fun save(
        profiles: List<TracePointService.TraceProfile>,
        activeProfileName: String,
        descriptionAreaOpened: Boolean,
        highlightingEnabled: Boolean,
        namePromptEnabled: Boolean,
        claudeAssistEnabled: Boolean,
        claudeAssistTarget: ClaudeAssistTarget
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
            namePromptEnabled = namePromptEnabled,
            claudeAssistEnabled = claudeAssistEnabled,
            claudeAssistTarget = claudeAssistTarget,
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

    /** Canonical global storage path: `<appDir>/<projectId>.xml`. */
    private fun allocateStorageFile(projectId: String): Path {
        val dir = GlobalStoragePaths.resolveAppDir()
        Files.createDirectories(dir)
        return dir.resolve("$projectId.xml")
    }

    private fun listProjectXmlFiles(): List<Path> {
        val dir = GlobalStoragePaths.resolveAppDir()
        if (!Files.isDirectory(dir)) return emptyList()
        return Files.list(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".xml") }
                .collect(Collectors.toList())
        }
    }

    /**
     * Case A lookup:
     * 1. Fast path — open `<projectId>.xml` when present
     * 2. Legacy fallback — scan other `*.xml` for matching `<projectId>`
     * 3. Best-effort rename legacy file → `<projectId>.xml`
     */
    private fun findDocumentByProjectId(projectId: String): ProjectDocument? {
        val canonical = allocateStorageFile(projectId)

        if (Files.isRegularFile(canonical)) {
            try {
                val doc = ProjectDataXml.parseFile(canonical)
                if (doc.projectId == projectId) {
                    return doc.copy(storageFile = canonical)
                }
            } catch (e: Exception) {
                log.debug("Canonical storage file unreadable $canonical", e)
            }
        }

        for (file in listProjectXmlFiles()) {
            if (file.toAbsolutePath().normalize() == canonical.toAbsolutePath().normalize()) continue
            try {
                val doc = ProjectDataXml.parseFile(file)
                if (doc.projectId != projectId) continue
                val migrated = migrateLegacyStorageFile(file, canonical)
                return doc.copy(storageFile = migrated)
            } catch (e: Exception) {
                log.debug("Skipping unreadable storage file $file", e)
            }
        }
        return null
    }

    /** Rename legacy FolderName.xml → projectId.xml when the target is free. */
    private fun migrateLegacyStorageFile(legacyFile: Path, canonicalFile: Path): Path {
        if (legacyFile.toAbsolutePath().normalize() == canonicalFile.toAbsolutePath().normalize()) {
            return legacyFile
        }
        if (Files.exists(canonicalFile)) return legacyFile
        return try {
            Files.move(legacyFile, canonicalFile)
            canonicalFile
        } catch (e: Exception) {
            log.debug("Could not migrate legacy storage $legacyFile → $canonicalFile", e)
            legacyFile
        }
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
}
