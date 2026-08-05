/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
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
 * - Case B: match by path — one match binds in place; multiple matches pick latest
 *   `updatedAt` then copy-on-write to a new `<projectId>.xml`
 * - Case C: no match → return null (do not create id/XML until first real use)
 *
 * Call [ensureCreated] before the first persist that should bind storage
 * (create trace point, add profile, import, or toolbar toggle).
 */
class ProjectStorage(private val projectBasePath: String) {
    private val log = Logger.getInstance(ProjectStorage::class.java)
    private val projectBase: Path = Paths.get(projectBasePath).toAbsolutePath().normalize()

    @Volatile
    private var boundFile: Path? = null

    @Volatile
    private var boundProjectId: String? = null

    /**
     * Resolve existing storage (Case A / B). Returns null when nothing exists yet
     * (lazy Case C — no disk writes).
     */
    fun resolveAndLoad(): ProjectDocument? {
        Files.createDirectories(GlobalStoragePaths.resolveAppDir())

        val existingId = ProjectIdFiles.readProjectId(projectBase)

        // Case A: match by id
        if (!existingId.isNullOrBlank()) {
            val byId = findDocumentByProjectId(existingId)
            if (byId != null) {
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

        // Case B: match by path
        val pathMatches = findDocumentsByPath(projectBase.toString())
        if (pathMatches.isNotEmpty()) {
            val selected = if (pathMatches.size == 1) {
                pathMatches[0]
            } else {
                pathMatches.maxByOrNull { it.updatedAt } ?: pathMatches[0]
            }

            if (pathMatches.size == 1) {
                ProjectIdFiles.writeProjectId(projectBase, selected.projectId)
                val updated = selected.copy(
                    path = projectBase.toString(),
                    updatedAt = System.currentTimeMillis(),
                    storageFile = selected.storageFile
                )
                bind(updated)
                save(updated)
                return updated
            }

            // Multiple path matches: copy-on-write from the latest
            val newId = generateProjectId()
            val newFile = allocateStorageFile(newId)
            val copied = selected.copy(
                projectId = newId,
                path = projectBase.toString(),
                updatedAt = System.currentTimeMillis(),
                storageFile = newFile,
                profiles = selected.profiles.map { profile ->
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

        // Case C: deferred — no project id / XML until ensureCreated()
        return null
    }

    /**
     * Bind storage for a new project (Case C) if not already bound.
     * Writes the local project id file and allocates the global XML path;
     * the first [save] writes the XML from in-memory state.
     * @return true when this call newly bound storage
     */
    fun ensureCreated(): Boolean {
        if (boundFile != null && boundProjectId != null) return false

        Files.createDirectories(GlobalStoragePaths.resolveAppDir())
        if (resolveAndLoad() != null) return true

        val newId = generateProjectId()
        val newFile = allocateStorageFile(newId)
        ProjectIdFiles.writeProjectId(projectBase, newId)
        boundProjectId = newId
        boundFile = newFile
        return true
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
        namePromptEnabled: Boolean
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

    private fun findDocumentsByPath(absolutePath: String): List<ProjectDocument> {
        val normalized = normalizePath(absolutePath)
        val matches = mutableListOf<ProjectDocument>()
        for (file in listProjectXmlFiles()) {
            try {
                val doc = ProjectDataXml.parseFile(file)
                if (normalizePath(doc.path) == normalized) {
                    matches.add(doc.copy(storageFile = file))
                }
            } catch (e: Exception) {
                log.debug("Skipping unreadable storage file $file", e)
            }
        }
        return matches
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
