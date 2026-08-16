/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.services

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.pidifa.codetracetree.domain.enums.NodeListenerEventType
import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.storage.AdvancedSettings
import com.pidifa.codetracetree.storage.AgentSignalFiles
import com.pidifa.codetracetree.storage.ExternalStorageWatcher
import com.pidifa.codetracetree.storage.ProjectDocument
import com.pidifa.codetracetree.storage.ProjectStorage
import com.pidifa.codetracetree.storage.StorageReadyWatcher
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@Service(Service.Level.PROJECT)
class TracePointService(private val project: Project) {

    companion object {
        const val DEFAULT_PROFILE_NAME = "main"
        private const val SELF_WRITE_IGNORE_MS = 1500L
        private const val PATH_VALIDITY_DEBOUNCE_MS = 350L
        private val LOG = Logger.getInstance(TracePointService::class.java)
    }

    data class TracePoint(
        val traceName: String = "",
        val traceType: TraceType = TraceType.LINE,
        val baseName: String = "",
        val tracePath: String = "",
        val lineNumber: Int = 0,
        val lineContent: String? = null,
        val isValid: Boolean = true,
        val totalOccurrences: Int = 0,
        val occurrenceIndex: Int = 0,
        val description: String? = ""
    ) {
        fun navigateTo(project: Project) {
            ApplicationManager.getApplication().runReadAction {
                val basePath = project.basePath
                if (basePath.isNullOrBlank()) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Cannot navigate: project has no base path",
                            "Navigate to Trace Point"
                        )
                    }
                    return@runReadAction
                }
                val fileUrl = "file:///$basePath/$tracePath"
                val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
                if (file == null) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Cannot find path: $tracePath in project $basePath",
                            "Navigate to Trace Point"
                        )
                    }
                    return@runReadAction
                }
                ApplicationManager.getApplication().invokeLater {
                    when (traceType) {
                        TraceType.DIRECTORY -> {
                            ToolWindowManager.getInstance(project)
                                .getToolWindow(ToolWindowId.PROJECT_VIEW)
                                ?.activate {
                                    ProjectView.getInstance(project).select(null, file, true)
                                }
                                ?: ProjectView.getInstance(project).select(null, file, true)
                        }
                        TraceType.FILE -> {
                            FileEditorManager.getInstance(project).openFile(file, true)
                        }
                        TraceType.LINE -> {
                            FileEditorManager.getInstance(project).openFile(file, true)
                            OpenFileDescriptor(project, file, (lineNumber - 1).coerceAtLeast(0), 0)
                                .navigate(true)
                        }
                    }
                }
            }
        }
    }

    data class TracePointNode(
        val id: String = "",
        var tracePoint: TracePoint = TracePoint(),
        var parentId: String? = null,
        var children: MutableList<TracePointNode> = mutableListOf()
    )

    data class TraceProfile(
        var name: String = DEFAULT_PROFILE_NAME,
        var tracePointNodes: MutableList<TracePointNode> = mutableListOf(),
        var expandedTracePointIds: MutableSet<String> = mutableSetOf()
    )

    private val listenersMap = mutableMapOf<NodeListenerEventType, MutableList<(List<TracePointNode>, Boolean) -> Unit>>()
    private val profileListeners = mutableListOf<() -> Unit>()
    private val selectedTracePointIds = mutableSetOf<String>()
    private var expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, DocumentListener>()
    private val highlighters = mutableMapOf<VirtualFile, MutableList<com.intellij.openapi.editor.markup.RangeHighlighter>>()
    private var isFileSystemRefreshing = false
    private var isHighlightingEnabled = true
    private var isDescriptionAreaOpened = false
    private var isNamePromptEnabled = true
    private var advancedSettings: AdvancedSettings = AdvancedSettings.defaults()

    private var profiles: MutableList<TraceProfile> = mutableListOf(TraceProfile(name = DEFAULT_PROFILE_NAME))
    private var activeProfileName: String = DEFAULT_PROFILE_NAME
    private var tracePointNodes: MutableList<TracePointNode> = mutableListOf()
    private val nodeMap = mutableMapOf<String, TracePointNode>()
    private val fileNodesMap = mutableMapOf<String, MutableList<TracePointNode>>()
    private var projectStorage: ProjectStorage? = null
    private var persistScheduled = false
    private var suppressPersist = false
    /** After persist, notify peer IDEs (debounced with schedulePersist). */
    private var pendingPeerProfileRefresh = false
    private var pendingPeerSettingsRefresh = false
    private var pendingPeerFullRefresh = false
    @Volatile
    private var ignoreExternalChangesUntilMs = 0L
    private var externalStorageWatcher: ExternalStorageWatcher? = null
    private var storageReadyWatcher: StorageReadyWatcher? = null
    private val pendingPathValidityPaths = ConcurrentHashMap.newKeySet<String>()
    private val pathValidityExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "code-trace-tree-path-validity").apply { isDaemon = true }
    }
    @Volatile
    private var pathValidityFuture: ScheduledFuture<*>? = null

    init {
        loadFromHybridStorage()
        startExternalStorageWatcher()

        Disposer.register(project, Disposable {
            pathValidityFuture?.cancel(false)
            pathValidityExecutor.shutdownNow()
            externalStorageWatcher?.close()
            externalStorageWatcher = null
            storageReadyWatcher?.close()
            storageReadyWatcher = null
            persistNow()
        })

        // Listen for file openings: rebind LINE availability, then attach listener and highlight
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    val changed = rebindLineNodesForFile(file)
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                    if (changed) notifyListeners()
                }
            }
        )

        // FILE/DIRECTORY existence only (LINE content is handled for open editors)
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (isFileSystemRefreshing) return
                    var scheduled = false
                    for (event in events) {
                        for (relativePath in relativePathsAffectedBy(event)) {
                            val nodes = fileNodesMap[relativePath] ?: continue
                            if (nodes.none {
                                    it.tracePoint.traceType == TraceType.FILE ||
                                        it.tracePoint.traceType == TraceType.DIRECTORY
                                }
                            ) {
                                continue
                            }
                            pendingPathValidityPaths.add(relativePath)
                            scheduled = true
                        }
                    }
                    if (scheduled) schedulePathValidityCheck()
                }
            }
        )

        // Validate trace points on initial load and file refresh
        ApplicationManager.getApplication().runReadAction {
            validateTracePointsOnLoad()
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
            notifyListeners()
        }

        // After VFS refresh: path traces + open LINE buffers only (not closed LINE files)
        VirtualFileManager.getInstance().addVirtualFileManagerListener(object : VirtualFileManagerListener {
            override fun beforeRefreshStart(isAsync: Boolean) {
                isFileSystemRefreshing = true
            }

            override fun afterRefreshFinish(isAsync: Boolean) {
                ApplicationManager.getApplication().runReadAction {
                    val pathChanged = revalidateAllPathTracePoints()
                    var lineChanged = false
                    FileEditorManager.getInstance(project).openFiles.forEach { file ->
                        if (rebindLineNodesForFile(file)) lineChanged = true
                        highlightTracePointsInFile(file)
                    }
                    if (pathChanged || lineChanged) notifyListeners()
                    isFileSystemRefreshing = false
                }
            }
        }, project)
    }

    private fun relativeProjectPath(file: VirtualFile): String? {
        val basePath = project.basePath ?: return null
        val prefix = "$basePath/"
        return file.path.removePrefix(prefix).takeIf { it != file.path }
            ?: file.path.removePrefix(basePath.replace('\\', '/') + "/").takeIf { it != file.path }
    }

    private fun relativeFromAbsolutePath(absolutePath: String): String? {
        val basePath = project.basePath ?: return null
        val normalized = absolutePath.replace('\\', '/')
        val prefix = basePath.replace('\\', '/') + "/"
        return if (normalized.startsWith(prefix)) normalized.removePrefix(prefix) else null
    }

    private fun relativePathsAffectedBy(event: VFileEvent): List<String> {
        val paths = mutableListOf<String>()
        relativeFromAbsolutePath(event.path)?.let { paths.add(it) }
        if (event is VFileMoveEvent) {
            relativeFromAbsolutePath(event.oldPath)?.let { paths.add(it) }
        }
        return paths
    }

    private fun schedulePathValidityCheck() {
        pathValidityFuture?.cancel(false)
        pathValidityFuture = pathValidityExecutor.schedule({
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val paths = pendingPathValidityPaths.toSet()
                pendingPathValidityPaths.clear()
                if (paths.isEmpty()) return@invokeLater
                val changed = revalidatePathTracePoints(paths)
                if (changed) notifyListeners()
            }
        }, PATH_VALIDITY_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /** Update isValid for FILE/DIRECTORY nodes at the given relative paths. */
    private fun revalidatePathTracePoints(relativePaths: Set<String>): Boolean {
        var changed = false
        ApplicationManager.getApplication().runReadAction {
            val basePath = project.basePath ?: return@runReadAction
            for (relativePath in relativePaths) {
                val nodes = fileNodesMap[relativePath] ?: continue
                val file = VirtualFileManager.getInstance()
                    .findFileByUrl("file:///$basePath/$relativePath")
                for (node in nodes) {
                    val tp = node.tracePoint
                    val valid = when (tp.traceType) {
                        TraceType.DIRECTORY -> file != null && file.isDirectory
                        TraceType.FILE -> file != null && !file.isDirectory
                        else -> continue
                    }
                    if (tp.isValid != valid) {
                        node.tracePoint = tp.copy(
                            isValid = valid,
                            totalOccurrences = 0,
                            occurrenceIndex = 0,
                            lineNumber = 0,
                            lineContent = null
                        )
                        changed = true
                    }
                }
            }
        }
        return changed
    }

    private fun revalidateAllPathTracePoints(): Boolean {
        val paths = fileNodesMap.filterValues { nodes ->
            nodes.any {
                it.tracePoint.traceType == TraceType.FILE ||
                    it.tracePoint.traceType == TraceType.DIRECTORY
            }
        }.keys
        return if (paths.isEmpty()) false else revalidatePathTracePoints(paths)
    }

    /**
     * Rebind LINE nodes in an opened editor against the document buffer.
     * Updates runtime `isValid` (and line/occurrence if the match moved).
     */
    private fun rebindLineNodesForFile(file: VirtualFile): Boolean {
        if (file.isDirectory) return false
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            val relativePath = relativeProjectPath(file)
                ?: project.basePath?.let { file.path.removePrefix("$it/") }
                    ?.takeIf { it != file.path }
                ?: return@runReadAction false
            val nodes = fileNodesMap[relativePath] ?: return@runReadAction false
            if (nodes.none { it.tracePoint.traceType == TraceType.LINE }) return@runReadAction false
            val document = FileDocumentManager.getInstance().getDocument(file)
                ?: return@runReadAction false
            val lines = document.text.split("\n")
            var changed = false
            for (node in nodes) {
                if (node.tracePoint.traceType != TraceType.LINE) continue
                val rebound = rebindLineTracePoint(node.tracePoint, lines)
                if (rebound != node.tracePoint) {
                    node.tracePoint = rebound
                    changed = true
                }
            }
            changed
        }
    }

    /**
     * Shared LINE rebind rules (script `trace_tree rebind` + load validate + VFS).
     * 1 exact, 2 unique content, 3 stable occurrence, 4 nearest match, else invalid.
     */
    private fun rebindLineTracePoint(tp: TracePoint, lines: List<String>): TracePoint {
        val content = tp.lineContent?.trim()
        if (content.isNullOrEmpty()) {
            return tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
        }
        val matches = lines.mapIndexedNotNull { i, line ->
            if (line.trim() == content) i + 1 else null
        }
        val total = matches.size
        if (total == 0) {
            return tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
        }

        val oldLine = tp.lineNumber
        val (newLine, newIndex) = when {
            oldLine in 1..lines.size && lines[oldLine - 1].trim() == content -> {
                oldLine to (matches.indexOf(oldLine) + 1)
            }
            total == 1 -> matches[0] to 1
            total == tp.totalOccurrences && tp.occurrenceIndex in 1..total -> {
                matches[tp.occurrenceIndex - 1] to tp.occurrenceIndex
            }
            else -> {
                val nearest = matches.minByOrNull { abs(it - oldLine) }!!
                nearest to (matches.indexOf(nearest) + 1)
            }
        }
        return tp.copy(
            lineNumber = newLine,
            totalOccurrences = total,
            occurrenceIndex = newIndex,
            isValid = true
        )
    }

    private fun loadFromHybridStorage() {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) {
            LOG.info("Code Trace Tree: project has no base path; using in-memory defaults")
            return
        }
        try {
            val storage = ProjectStorage(basePath)
            projectStorage = storage
            val doc = storage.resolveAndLoad()
            if (doc != null) {
                applyDocument(doc, validate = false, notifyUi = false)
            }
            // Lazy Case C: keep in-memory defaults until first real use
        } catch (e: Exception) {
            LOG.warn("Code Trace Tree: failed to load hybrid storage; using defaults", e)
        }
    }

    /**
     * Create local project id + bind global XML path if this project has no storage yet.
     * Call before the first persist for create / profile / import / toolbar toggles.
     */
    fun ensureStorage(): Boolean {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) return false
        val storage = projectStorage ?: ProjectStorage(basePath).also { projectStorage = it }
        if (storage.boundProjectId() != null) return false
        val created = storage.ensureCreated()
        if (created) {
            startExternalStorageWatcher()
        }
        return created
    }

    private fun startExternalStorageWatcher(replayExistingRefresh: Boolean = true) {
        val basePath = project.basePath
        if (basePath.isNullOrBlank()) return

        val projectId = projectStorage?.boundProjectId()
        if (projectId.isNullOrBlank()) {
            // Case C: do not create storage; watch global <projectId>.storage-ready.
            externalStorageWatcher?.close()
            externalStorageWatcher = null
            if (storageReadyWatcher == null) {
                val watcher = StorageReadyWatcher { signalProjectId ->
                    ApplicationManager.getApplication().invokeLater {
                        if (project.isDisposed) return@invokeLater
                        if (!handleStorageReadySignal(signalProjectId)) {
                            storageReadyWatcher?.clearSeen(signalProjectId)
                        }
                    }
                }
                storageReadyWatcher = watcher
                watcher.start()
            }
            return
        }

        storageReadyWatcher?.close()
        storageReadyWatcher = null
        externalStorageWatcher?.close()
        val watcher = ExternalStorageWatcher(
            projectId = projectId,
            onFullRefresh = { reason ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        // Agent signals must not be dropped by the self-write ignore window.
                        reloadFromExternalStorage(reason, bypassIgnoreWindow = true)
                    }
                }
            },
            onProfileRefresh = {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        handleExternalProfileRefreshRequest()
                    }
                }
            },
            onSettingsRefresh = {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        handleExternalSettingsRefreshRequest()
                    }
                }
            },
            onSelectRequest = {
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        handleExternalSelectRequest()
                    }
                }
            }
        )
        externalStorageWatcher = watcher
        watcher.start(replayExistingRefresh = replayExistingRefresh)
    }

    /**
     * Agent wrote `signals/<projectId>.storage-ready` after creating global XML (Case C).
     * Prefer path from the signal body; fall back to XML `<path>` for legacy signals.
     * @return true when handled (bound or permanently skipped); false to retry later.
     */
    private fun handleStorageReadySignal(signalProjectId: String): Boolean {
        if (projectStorage?.boundProjectId() != null) {
            startExternalStorageWatcher()
            return true
        }
        val basePath = project.basePath ?: return true
        val storage = projectStorage ?: ProjectStorage(basePath).also { projectStorage = it }
        val signalPath = AgentSignalFiles.readStorageReadyProjectPath(signalProjectId)
            .ifBlank { null }
        val bound = storage.tryBindFromStorageReady(signalProjectId, signalPath) ?: return false
        if (!bound) return true

        val doc = storage.reloadBoundDocument() ?: return false
        suppressPersist = true
        try {
            applyDocument(doc, validate = true, notifyUi = true)
        } finally {
            suppressPersist = false
        }
        // Data already loaded; skip replaying request_refresh written alongside storage-ready.
        startExternalStorageWatcher(replayExistingRefresh = false)
        return true
    }

    /**
     * Reloads the bound global XML into memory and refreshes the tool window / highlights.
     * Called when a global `request_refresh` signal is written.
     */
    fun reloadFromExternalStorage(
        reason: String = "manual",
        bypassIgnoreWindow: Boolean = false
    ): Boolean {
        val storage = projectStorage ?: return false
        if (!bypassIgnoreWindow && System.currentTimeMillis() < ignoreExternalChangesUntilMs) return false
        val doc = storage.reloadBoundDocument() ?: return false
        LOG.info("Code Trace Tree: reloading from external storage ($reason)")
        suppressPersist = true
        try {
            applyDocument(doc, validate = true, notifyUi = true)
        } finally {
            suppressPersist = false
        }
        return true
    }

    /**
     * Reloads one profile from the bound XML into memory.
     * Does not change [activeProfileName] or project toolbar flags.
     * @param profileName blank/null → active profile
     */
    fun reloadProfileFromExternalStorage(
        profileName: String? = null,
        bypassIgnoreWindow: Boolean = false
    ): Boolean {
        val storage = projectStorage ?: return false
        if (!bypassIgnoreWindow && System.currentTimeMillis() < ignoreExternalChangesUntilMs) return false
        val doc = storage.reloadBoundDocument() ?: return false
        val name = profileName?.trim().orEmpty().ifEmpty { activeProfileName }
        val incoming = doc.profiles.find { it.name == name } ?: return false
        LOG.info("Code Trace Tree: reloading profile '$name' from external storage")
        suppressPersist = true
        try {
            val cloned = TraceProfile(
                name = incoming.name.ifBlank { DEFAULT_PROFILE_NAME },
                tracePointNodes = incoming.tracePointNodes,
                expandedTracePointIds = incoming.expandedTracePointIds.toMutableSet()
            )
            val idx = profiles.indexOfFirst { it.name == cloned.name }
            if (idx >= 0) {
                profiles[idx] = cloned
            } else {
                profiles.add(cloned)
            }
            if (cloned.name == activeProfileName) {
                // Keep tree selection across peer/agent profile refresh (and ignore self-echo).
                loadActiveProfileFromStore(preserveSelection = true)
            }
            notifyProfileListeners()
        } finally {
            suppressPersist = false
        }
        return true
    }

    /** Handles `<projectId>.request_refresh_profile` (body = profile name; empty → active). */
    fun handleExternalProfileRefreshRequest() {
        val projectId = projectStorage?.boundProjectId() ?: return
        val request = AgentSignalFiles.refreshProfilePath(projectId)
        if (!AgentSignalFiles.isFresh(request)) return
        val name = AgentSignalFiles.readProfileRefreshName(request)
        // Respect self-write ignore window so our own move/persist signal does not
        // clear selection a moment later via loadActiveProfileFromStore().
        reloadProfileFromExternalStorage(name.ifBlank { null }, bypassIgnoreWindow = false)
    }

    /**
     * Reloads project toolbar flags, advancedSettings, and activeProfileName from XML.
     * Does not replace other profile trees unless the active profile name changed.
     */
    fun reloadSettingsFromExternalStorage(bypassIgnoreWindow: Boolean = false): Boolean {
        val storage = projectStorage ?: return false
        if (!bypassIgnoreWindow && System.currentTimeMillis() < ignoreExternalChangesUntilMs) return false
        val doc = storage.reloadBoundDocument() ?: return false
        LOG.info("Code Trace Tree: reloading settings from external storage")
        suppressPersist = true
        try {
            val prevActive = activeProfileName
            activeProfileName = doc.activeProfileName
                .takeIf { name -> profiles.any { it.name == name } }
                ?: profiles.firstOrNull()?.name
                ?: DEFAULT_PROFILE_NAME
            isDescriptionAreaOpened = doc.descriptionAreaOpened
            isHighlightingEnabled = doc.highlightingEnabled
            isNamePromptEnabled = doc.namePromptEnabled
            advancedSettings = doc.advancedSettings
            if (prevActive != activeProfileName) {
                loadActiveProfileFromStore()
                notifyProfileListeners()
            } else {
                ApplicationManager.getApplication().runReadAction {
                    FileEditorManager.getInstance(project).openFiles.forEach { file ->
                        if (isHighlightingEnabled) highlightTracePointsInFile(file) else removeHighlights(file)
                    }
                }
            }
        } finally {
            suppressPersist = false
        }
        return true
    }

    /** Handles `<projectId>.request_refresh_settings`. */
    fun handleExternalSettingsRefreshRequest() {
        val projectId = projectStorage?.boundProjectId() ?: return
        val request = AgentSignalFiles.refreshSettingsPath(projectId)
        if (!AgentSignalFiles.isFresh(request)) return
        reloadSettingsFromExternalStorage(bypassIgnoreWindow = false)
    }

    /**
     * Selects / reveals trace points listed in the global select signal
     * (`signals/<projectId>.select_trace_points`, one UUID per line).
     * TTL-stale files are ignored; fresh signals are left for other windows (TTL cleans up).
     * When exactly one id resolves in the current profile, also navigates to its source.
     */
    fun handleExternalSelectRequest() {
        val projectId = projectStorage?.boundProjectId() ?: return
        val request = AgentSignalFiles.selectPath(projectId)
        if (!AgentSignalFiles.isFresh(request)) return

        val requestedIds = try {
            Files.readAllLines(request, StandardCharsets.UTF_8)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        } catch (e: Exception) {
            LOG.warn("Code Trace Tree: failed to read select signal $request", e)
            return
        }
        // Leave the signal file for other IDE windows; TTL cleans it up.

        val resolved = requestedIds.mapNotNull { getTracePointNodeById(it) }
        if (resolved.isEmpty()) return

        val resolvedIds = resolved.map { it.id }.toSet()
        val navigateTarget = resolved.singleOrNull()?.tracePoint

        fun applySelection() {
            revealTracePointsInTree(resolvedIds)
            navigateTarget?.navigateTo(project)
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Trace Tree")
        if (toolWindow != null) {
            toolWindow.show { applySelection() }
        } else {
            applySelection()
        }
    }

    private fun applyDocument(doc: ProjectDocument, validate: Boolean, notifyUi: Boolean) {
        isHighlightingEnabled = doc.highlightingEnabled
        isDescriptionAreaOpened = doc.descriptionAreaOpened
        isNamePromptEnabled = doc.namePromptEnabled
        advancedSettings = doc.advancedSettings
        profiles = doc.profiles.map {
            TraceProfile(
                name = it.name.ifBlank { DEFAULT_PROFILE_NAME },
                tracePointNodes = it.tracePointNodes,
                expandedTracePointIds = it.expandedTracePointIds.toMutableSet()
            )
        }.toMutableList()
        if (profiles.isEmpty()) {
            profiles.add(TraceProfile(name = DEFAULT_PROFILE_NAME))
        }
        activeProfileName = doc.activeProfileName
            .takeIf { name -> profiles.any { it.name == name } }
            ?: profiles.first().name
        val profile = profiles.find { it.name == activeProfileName } ?: profiles.first()
        if (notifyUi || validate) {
            clearAllHighlights()
            selectedTracePointIds.clear()
        }
        tracePointNodes = profile.tracePointNodes
        expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
        rebuildNodeMapAndFileNodesMap()
        if (validate) {
            validateTracePointsOnLoad()
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
            reattachListenersAndHighlights()
        }
        if (notifyUi) {
            notifyProfileListeners()
            val copy = getTracePoints()
            listenersMap[NodeListenerEventType.FULL_UPDATE]?.forEach { it(copy, true) }
        }
    }

    /** Persist profiles to global storage (debounced on the EDT). */
    fun schedulePersist() {
        if (suppressPersist) return
        if (projectStorage == null) return
        if (persistScheduled) return
        persistScheduled = true
        ApplicationManager.getApplication().invokeLater {
            persistScheduled = false
            if (!project.isDisposed && !suppressPersist) {
                persistNow()
            }
        }
    }

    /** Mark peer profile refresh (structure ops). Prefer calling before [notifyListeners]. */
    fun markPeerProfileRefresh() {
        pendingPeerProfileRefresh = true
    }

    /** Mark peer settings refresh. */
    fun markPeerSettingsRefresh() {
        pendingPeerSettingsRefresh = true
    }

    /** Mark peer full refresh (profile add/delete/switch/import). */
    fun markPeerFullRefresh() {
        pendingPeerFullRefresh = true
    }

    /** Persist and ask peers to reload this profile's tree. */
    fun scheduleStructurePersist() {
        markPeerProfileRefresh()
        schedulePersist()
    }

    /** Persist and ask peers to reload project settings / active profile. */
    fun scheduleSettingsPersist() {
        markPeerSettingsRefresh()
        schedulePersist()
    }

    /** Persist and ask peers for a full storage reload. */
    fun scheduleFullPeerPersist() {
        markPeerFullRefresh()
        schedulePersist()
    }

    private fun persistNow() {
        val storage = projectStorage ?: return
        ignoreExternalChangesUntilMs = System.currentTimeMillis() + SELF_WRITE_IGNORE_MS
        syncActiveProfileToStore()
        storage.save(
            profiles = profiles,
            activeProfileName = activeProfileName,
            descriptionAreaOpened = isDescriptionAreaOpened,
            highlightingEnabled = isHighlightingEnabled,
            namePromptEnabled = isNamePromptEnabled,
            advancedSettings = advancedSettings
        )
        emitPendingPeerSignals()
        externalStorageWatcher?.refreshRegistrations()
    }

    private fun emitPendingPeerSignals() {
        val projectId = projectStorage?.boundProjectId()
        val root = project.basePath
        val full = pendingPeerFullRefresh
        val profile = pendingPeerProfileRefresh
        val settings = pendingPeerSettingsRefresh
        pendingPeerFullRefresh = false
        pendingPeerProfileRefresh = false
        pendingPeerSettingsRefresh = false
        if (projectId.isNullOrBlank()) return
        if (full) {
            AgentSignalFiles.writeRequestRefresh(projectId, root)
            return
        }
        if (profile) {
            AgentSignalFiles.writeRequestRefreshProfile(projectId, activeProfileName, root)
        }
        if (settings) {
            AgentSignalFiles.writeRequestRefreshSettings(projectId, root)
        }
    }

    // === Highlighting ===

    fun isHighlightingEnabled(): Boolean = isHighlightingEnabled
    fun isDescriptionAreaOpened(): Boolean = isDescriptionAreaOpened
    fun isNamePromptEnabled(): Boolean = isNamePromptEnabled
    fun getAdvancedSettings(): AdvancedSettings = advancedSettings

    fun setAdvancedSettings(settings: AdvancedSettings) {
        ensureStorage()
        advancedSettings = AdvancedSettings(
            highlightLineBackgroundLight = AdvancedSettings.normalizeHex(settings.highlightLineBackgroundLight)
                ?: AdvancedSettings.DEFAULT_HIGHLIGHT_LIGHT,
            highlightLineBackgroundDark = AdvancedSettings.normalizeHex(settings.highlightLineBackgroundDark)
                ?: AdvancedSettings.DEFAULT_HIGHLIGHT_DARK
        )
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                if (isHighlightingEnabled) highlightTracePointsInFile(file)
            }
        }
        scheduleSettingsPersist()
    }

    fun setDescriptionAreaOpened(opened: Boolean) {
        ensureStorage()
        isDescriptionAreaOpened = opened
        scheduleSettingsPersist()
    }

    fun setNamePromptEnabled(enabled: Boolean) {
        ensureStorage()
        isNamePromptEnabled = enabled
        scheduleSettingsPersist()
    }

    fun setHighlightingEnabled(enabled: Boolean) {
        ensureStorage()
        isHighlightingEnabled = enabled
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                if (enabled) highlightTracePointsInFile(file) else removeHighlights(file)
            }
        }
        scheduleSettingsPersist()
    }

    fun highlightTracePointsInFile(file: VirtualFile) {
        if (!isHighlightingEnabled) return
        ApplicationManager.getApplication().runReadAction {
            // Remove existing highlighters for this file
            removeHighlights(file)

            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val relevant = fileNodesMap[relativePath]
                ?.filter { it.tracePoint.traceType == TraceType.LINE && it.tracePoint.isValid }
                ?: emptyList()
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
            val editors = FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<TextEditor>()
            if (editors.isEmpty()) return@runReadAction

            // Use configured highlight colors (light / dark theme)
            val textAttributes = TextAttributes().apply {
                backgroundColor = JBColor(
                    advancedSettings.lightColor(),
                    advancedSettings.darkColor()
                )
            }

            val newHighlighters = mutableListOf<com.intellij.openapi.editor.markup.RangeHighlighter>()
            for (tp in relevant) {
                if (tp.tracePoint.lineNumber <= document.lineCount) {
                    val start = document.getLineStartOffset(tp.tracePoint.lineNumber - 1)
                    val end = document.getLineEndOffset(tp.tracePoint.lineNumber - 1)
                    editors.forEach { editor ->
                        val h = editor.editor.markupModel.addRangeHighlighter(
                            start, end,
                            HighlighterLayer.SELECTION - 1,
                            textAttributes,
                            HighlighterTargetArea.LINES_IN_RANGE
                        )
                        newHighlighters.add(h)
                    }
                }
            }
            highlighters[file] = newHighlighters
        }
    }

    private fun removeHighlights(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            highlighters[file]?.forEach { it.dispose() }
            highlighters.remove(file)
        }
    }

    fun getLineOccurrences(document: com.intellij.openapi.editor.Document, content: String?): Pair<Int, List<Int>> {
        if (content.isNullOrBlank()) return Pair(0, emptyList())
        val lines = document.text.split("\n")
        val matching = lines.mapIndexedNotNull { i, line -> if (line.trim() == content.trim()) i + 1 else null }
        return Pair(matching.size, matching)
    }

    private fun rebuildNodeMapAndFileNodesMap() {
        nodeMap.clear()
        fileNodesMap.clear()
        fun walk(node: TracePointNode) {
            nodeMap[node.id] = node
            fileNodesMap.getOrPut(node.tracePoint.tracePath) { mutableListOf() }
                .add(node)
            node.children.forEach { child ->
                child.parentId = node.id
                walk(child)
            }
        }
        tracePointNodes.forEach { walk(it) }
    }


    // A document listener will not be added for the same file
    fun attachDocumentListener(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val affectedFileNodes = fileNodesMap[relativePath]
            val hasLineTracePoints = affectedFileNodes.orEmpty()
                .any { it.tracePoint.traceType == TraceType.LINE }
            if (hasLineTracePoints && !monitoredDocuments.containsKey(file)) {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
                val listener = object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        // Skip processing if event is due to file system refresh
                        if (isFileSystemRefreshing) return

                        val docFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                        val docPath = docFile.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
                        val affectedNodes = getTraceNodesByFilePath(docPath)
                        if (affectedNodes==null || affectedNodes.isEmpty()) return

                        ApplicationManager.getApplication().runReadAction {
                            // Bulk / agent rewrite from file start: offset math collapses tips to line 1.
                            if (shouldContentRebindDocumentEvent(event)) {
                                val changed = rebindLineNodesForFile(docFile)
                                highlightTracePointsInFile(docFile)
                                if (changed) notifyListeners()
                                return@runReadAction
                            }

                            val newLines = event.document.text.split("\n")
                            val oldLinesCount = event.oldFragment.toString().count { it == '\n' }
                            val newLinesCount = event.newFragment.toString().count { it == '\n' }
                            val lineOffset = newLinesCount - oldLinesCount
                            val changedLine = event.document.getLineNumber(event.offset) + 1

                            // Negative shift from line 1 that would clamp tips → content rebind.
                            if (changedLine == 1 && lineOffset < 0 &&
                                affectedNodes.any {
                                    it.tracePoint.traceType == TraceType.LINE &&
                                        it.tracePoint.isValid &&
                                        it.tracePoint.lineNumber + lineOffset < 1
                                }
                            ) {
                                val changed = rebindLineNodesForFile(docFile)
                                highlightTracePointsInFile(docFile)
                                if (changed) notifyListeners()
                                return@runReadAction
                            }

                            val updatedNodes = mutableListOf<TracePointNode>()
                            for( tracePointNode:TracePointNode in affectedNodes ){
                                updateNodeWhenDocChanged(event.document,event.offset, tracePointNode, docPath, newLines, lineOffset, changedLine,updatedNodes)
                            }
                            highlightTracePointsInFile(docFile)
                            notifyListeners(NodeListenerEventType.PARTIAL_UPDATE,updatedNodes )
                        }
                    }
                }
                document.addDocumentListener(listener, project)
                monitoredDocuments[file] = listener
            }
        }
    }


    private fun shouldContentRebindDocumentEvent(event: DocumentEvent): Boolean {
        if (event.offset != 0) return false
        val oldDocLen = event.document.textLength - event.newLength + event.oldLength
        if (oldDocLen > 0 && event.oldLength == oldDocLen) return true
        val oldLineBreaks = event.oldFragment.count { it == '\n' }
        val newLineBreaks = event.newFragment.count { it == '\n' }
        // Multi-line replace/insert from the start of the file (agent patch / rewrite).
        return oldLineBreaks >= 1 || newLineBreaks >= 2
    }

    private fun updateNodeWhenDocChanged(
        document: com.intellij.openapi.editor.Document,
        offset: Int,  // The cursor position (character offset) in the document during this editing operation.
        node: TracePointNode,
        filePath: String,
        newLines: List<String>,
        lineOffset: Int,
        changedLine: Int,
        updatedNodes: MutableList<TracePointNode>
    ) {
        if (node.tracePoint.tracePath != filePath) {
            return
        }
        if (node.tracePoint.traceType != TraceType.LINE) {
            return
        }

        val tp = node.tracePoint
        var updated = false

        if (!tp.isValid) {
            val valid = newLines.getOrNull(tp.lineNumber - 1)?.trim() == tp.lineContent?.trim()
            if (valid) {
                node.tracePoint = tp.copy(isValid = true)
                updated = true
            }
        } else {
            val lineStartOffset = document.getLineStartOffset(changedLine - 1)
            val isNewLineAtLineStart = (offset == lineStartOffset && lineOffset > 0)
            when {
                // Press Enter at the beginning of the current line:
                // This will move the tracepoint down to the next line of code.
                tp.lineNumber == changedLine && isNewLineAtLineStart -> {
                    val newLineNum = tp.lineNumber + lineOffset
                    val (total, matches) = getLineOccurrences(document, tp.lineContent)
                    val occIdx = matches.indexOf(newLineNum) + 1
                    node.tracePoint = tp.copy(
                        lineNumber = newLineNum,
                        isValid = occIdx > 0,
                        totalOccurrences = total,
                        occurrenceIndex = occIdx.coerceAtLeast(0)
                    )
                    updated = true
                }
                // When the edit happens on the trace point line, keep the line number unchanged and update only the content.
                tp.lineNumber == changedLine-> {
                    val newContent = newLines.getOrNull(changedLine - 1)?.trim()
                    val (total, matches) = getLineOccurrences(document, newContent)
                    val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(changedLine) + 1
                    val newTp = tp.copy(
                        lineContent = newContent,
                        isValid = newContent != null,
                        totalOccurrences = total,
                        occurrenceIndex = occIdx.coerceAtLeast(0)
                    )
                    if (tp != newTp) {
                        node.tracePoint = newTp
                        updated = true
                    }
                }
                // Move only the nodes whose trace point line number is below the changed line.
                // Keep lineContent (anchor); never clamp to 1 — that corrupts tips on bulk deletes.
                tp.lineNumber > changedLine && lineOffset != 0 -> {
                    val newLineNum = tp.lineNumber + lineOffset
                    if (newLineNum < 1) {
                        return
                    }
                    val (total, matches) = getLineOccurrences(document, tp.lineContent)
                    val occIdx = matches.indexOf(newLineNum) + 1
                    val stillThere = occIdx > 0
                    val newTp = tp.copy(
                        lineNumber = if (stillThere) newLineNum else tp.lineNumber,
                        isValid = stillThere,
                        totalOccurrences = total,
                        occurrenceIndex = if (stillThere) occIdx else 0
                    )
                    if (tp != newTp) {
                        node.tracePoint = newTp
                        updated = true
                    }
                }
            }
        }

        if (updated) updatedNodes.add(node)
        //node.children.forEach { updateNodeRecursively(document, offset,it, filePath, newLines, lineOffset, changedLine, updatedNodes) }
    }


    /**
     * Reload bound XML (bypass ignore window), then recheck LINE / FILE / DIRECTORY nodes.
     * Reload already validates; falls back to in-memory validate when reload fails.
     */
    fun recheckAllTracePoints() {
        refreshMissingTracePathsFromDisk()
        val reloaded = reloadFromExternalStorage("recheck", bypassIgnoreWindow = true)
        if (!reloaded) {
            validateTracePointsOnLoad()
        }
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
        }
        val copy = getTracePoints()
        listenersMap[NodeListenerEventType.FULL_UPDATE]?.forEach { it(copy, false) }
    }

    /** VFS refresh for missing paths (must not run inside a read action). */
    private fun refreshMissingTracePathsFromDisk() {
        val basePath = project.basePath ?: return
        fun walk(node: TracePointNode) {
            val tracePath = node.tracePoint.tracePath
            if (tracePath.isNotEmpty()) {
                val cached = VirtualFileManager.getInstance().findFileByUrl("file:///$basePath/$tracePath")
                if (cached == null) {
                    LocalFileSystem.getInstance().refreshAndFindFileByPath("$basePath/$tracePath")
                }
            }
            node.children.forEach(::walk)
        }
        tracePointNodes.forEach(::walk)
    }

    private fun validateTracePointsOnLoad() {
        ApplicationManager.getApplication().runReadAction {
            fun validateNode(node: TracePointNode) {
                val tp = node.tracePoint
                val basePath = project.basePath
                if (node.id.isEmpty() || tp.tracePath.isEmpty() || basePath.isNullOrBlank()) {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                    return
                }

                val file = VirtualFileManager.getInstance().findFileByUrl("file:///$basePath/${tp.tracePath}")
                if (file == null) {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                    return
                }

                when (tp.traceType) {
                    TraceType.DIRECTORY -> {
                        node.tracePoint = tp.copy(
                            isValid = file.isDirectory,
                            totalOccurrences = 0,
                            occurrenceIndex = 0,
                            lineNumber = 0,
                            lineContent = null
                        )
                    }
                    TraceType.FILE -> {
                        node.tracePoint = tp.copy(
                            isValid = !file.isDirectory,
                            totalOccurrences = 0,
                            occurrenceIndex = 0,
                            lineNumber = 0,
                            lineContent = null
                        )
                    }
                    TraceType.LINE -> {
                        if (tp.lineContent == null || file.isDirectory) {
                            node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                            return
                        }
                        val doc = FileDocumentManager.getInstance().getDocument(file) ?: run {
                            node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                            return
                        }
                        val lines = doc.text.split("\n")
                        node.tracePoint = rebindLineTracePoint(tp, lines)
                    }
                }
            }

            tracePointNodes.forEach { validateRecursively(it, ::validateNode) }
        }
    }

    private fun validateRecursively(node: TracePointNode, validator: (TracePointNode) -> Unit) {
        validator(node)
        node.children.forEach { validateRecursively(it, validator) }
    }

    fun addTracePoint(
        name: String,
        file: VirtualFile,
        lineNumber: Int,
        parentId: String? = null,
        description: String = ""
    ): String? {
        ensureStorage()
        return ApplicationManager.getApplication().runReadAction<String?> {
            val document = FileDocumentManager.getInstance().getDocument(file)
            val lineContent = document?.let {
                val start = it.getLineStartOffset(lineNumber - 1)
                val end = it.getLineEndOffset(lineNumber - 1)
                it.getText(TextRange(start, end)).trim()
            }
            if (lineContent.isNullOrEmpty()) {
                return@runReadAction null
            }
            val (totalOccurrences, matchingLines) = if (document != null) {
                getLineOccurrences(document, lineContent)
            } else Pair(0, emptyList())
            val occurrenceIndex = if (lineContent != null) matchingLines.indexOf(lineNumber) + 1 else 0

            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val baseName = file.name
            val id = UUID.randomUUID().toString()

            val tracePoint = TracePoint(
                traceName = name,
                traceType = TraceType.LINE,
                tracePath = relativePath,
                baseName = baseName,
                lineNumber = lineNumber,
                lineContent = lineContent,
                isValid = document != null,
                totalOccurrences = totalOccurrences,
                occurrenceIndex = occurrenceIndex,
                description = description
            )

            insertTracePointNode(TracePointNode(id, tracePoint), parentId)
            id
        }
    }

    /**
     * Adds a FILE or DIRECTORY trace point from Project View (no line anchor).
     */
    fun addPathTracePoint(
        name: String,
        file: VirtualFile,
        parentId: String? = null,
        description: String = ""
    ): String? {
        ensureStorage()
        return ApplicationManager.getApplication().runReadAction<String?> {
            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val kind = if (file.isDirectory) TraceType.DIRECTORY else TraceType.FILE
            val id = UUID.randomUUID().toString()
            val tracePoint = TracePoint(
                traceName = name,
                traceType = kind,
                tracePath = relativePath,
                baseName = file.name,
                lineNumber = 0,
                lineContent = null,
                isValid = true,
                totalOccurrences = 0,
                occurrenceIndex = 0,
                description = description
            )
            insertTracePointNode(TracePointNode(id, tracePoint), parentId)
            id
        }
    }

    private fun insertTracePointNode(newNode: TracePointNode, parentId: String?) {
        if (parentId == null) {
            tracePointNodes.add(newNode)
        } else {
            nodeMap[parentId]?.children?.add(newNode)?.also { newNode.parentId = nodeMap[parentId]?.id }
        }
        nodeMap[newNode.id] = newNode
        fileNodesMap.getOrPut(newNode.tracePoint.tracePath) { mutableListOf() }
            .add(newNode)
    }

    fun updateTracePointDescription(id: String, newDescription: String) {
        ApplicationManager.getApplication().runReadAction {
            nodeMap[id]?.let {
                it.tracePoint = it.tracePoint.copy(description = newDescription)
                scheduleStructurePersist()
            }
        }
    }

    fun renameTracePoint(id: String, newName: String) {
        ApplicationManager.getApplication().runReadAction {
            nodeMap[id]?.let {
                it.tracePoint = it.tracePoint.copy(traceName = newName)
                markPeerProfileRefresh()
                notifyListeners()
            }
        }
    }

    fun deleteTracePointsWithChildren(ids: List<String>) {
        ApplicationManager.getApplication().runReadAction {
            val toDelete = mutableSetOf<String>().apply { addAll(ids) }
            fun collect(node: TracePointNode) {
                toDelete.add(node.id)
                node.children.forEach { collect(it) }
            }
            ids.forEach { nodeMap[it]?.let { collect(it) } }

            val affectedFiles = toDelete.mapNotNull { nodeMap[it]?.tracePoint?.tracePath }.distinct()

            tracePointNodes.removeIf { toDelete.contains(it.id) }
            tracePointNodes.forEach { pruneRecursively(it, toDelete) }

            selectedTracePointIds.removeAll(toDelete)
            expandedTracePointIds.removeAll(toDelete)
            rebuildNodeMapAndFileNodesMap()

            affectedFiles.forEach { path ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$path")
                file?.let {
                    highlightTracePointsInFile(it)
                }
            }
            markPeerProfileRefresh()
            notifyListeners()
        }
    }

    /** Count invalid nodes in the active profile (current isValid flags). */
    fun countInvalidTracePoints(): Int {
        var count = 0
        fun walk(node: TracePointNode) {
            if (!node.tracePoint.isValid) count++
            node.children.forEach { walk(it) }
        }
        tracePointNodes.forEach { walk(it) }
        return count
    }

    /**
     * Remove invalid nodes from the active profile.
     * Valid children of an invalid parent are reparented in place.
     * @return number of nodes removed
     */
    fun removeInvalidTracePoints(): Int {
        val removedIds = mutableListOf<String>()

        fun prune(nodes: MutableList<TracePointNode>, parentId: String?) {
            var i = nodes.size - 1
            while (i >= 0) {
                val node = nodes[i]
                prune(node.children, node.id)
                if (!node.tracePoint.isValid) {
                    removedIds.add(node.id)
                    node.children.forEach { it.parentId = parentId }
                    val children = node.children.toList()
                    node.children.clear()
                    nodes.removeAt(i)
                    nodes.addAll(i, children)
                }
                i--
            }
        }

        ApplicationManager.getApplication().runReadAction {
            prune(tracePointNodes, null)
            if (removedIds.isEmpty()) return@runReadAction
            selectedTracePointIds.removeAll(removedIds.toSet())
            expandedTracePointIds.removeAll(removedIds.toSet())
            rebuildNodeMapAndFileNodesMap()
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
            markPeerProfileRefresh()
            notifyListeners()
        }
        return removedIds.size
    }

    private fun pruneRecursively(node: TracePointNode, toDelete: Set<String>) {
        node.children.removeIf { toDelete.contains(it.id) }
        node.children.forEach { pruneRecursively(it, toDelete) }
    }

    fun findTracePointNodes(predicate: (TracePointNode) -> Boolean): List<TracePointNode> {
        val result = mutableListOf<TracePointNode>()

        fun walk(node: TracePointNode) {
            if (predicate(node)) result.add(node)
            node.children.forEach { walk(it) }
        }

        tracePointNodes.forEach { walk(it) }
        return result
    }

    fun traverseTracePointNodes(visitor: (TracePointNode) -> TracePointNode) {
        fun walk(node: TracePointNode): TracePointNode {
            val transformedNode = visitor(node)
            node.children.map { walk(it) }
            return transformedNode
        }
        // Transform all root nodes and update the tracePointNodes collection
        tracePointNodes = tracePointNodes.map { walk(it) }.toMutableList()
    }


    fun anyTracePointNode(predicate: (TracePointNode) -> Boolean): Boolean {
        fun walk(node: TracePointNode): Boolean {
            if (predicate(node)) return true
            return node.children.any { walk(it) }
        }
        return tracePointNodes.any { walk(it) }
    }


    // Add a document listener if one doesn't exist for the file
    // Refresh the highlighted lines in the file
    fun refreshDocumentListener(updatedFilePaths: Set<String>) {
        ApplicationManager.getApplication().runReadAction {
            updatedFilePaths.forEach { path ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$path")
                file?.let {
                    attachDocumentListener(it)
                    highlightTracePointsInFile(it)
                }
            }
        }
    }

    fun getTracePoints(): MutableList<TracePointNode>  {
        return this.tracePointNodes
    }

    fun addRootTracePoint(tracePoint: TracePointNode){
        if(tracePoint.parentId==null)this.tracePointNodes.add(tracePoint)
    }

    fun removeRootTracePoint(tpNode: TracePointNode): Boolean {
        val iterator = tracePointNodes.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == tpNode.id) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    fun addRootTracePointNextTo(tracePoint: TracePointNode, id: String) {
        if (tracePoint.parentId != null) return
        val index = tracePointNodes.indexOfFirst { it.id == id }
        if (index != -1) {
            tracePointNodes.add(index + 1, tracePoint)
        } else {
            tracePointNodes.add(tracePoint)
        }
    }



    fun getTracePointNodeById(id: String): TracePointNode?  {
        return this.nodeMap[id]
    }

    fun findRootParentId(node: TracePointNode): String?  {
        var tempTP: TracePointService.TracePointNode? = node
        var rootParentId: String? = null
        while (tempTP != null) {
            rootParentId = tempTP.id
            if (tempTP.parentId == null) {
                break
            }
            tempTP = getTracePointNodeById(tempTP.parentId!!)
        }
        return rootParentId
    }

    fun updateInFileNodesMap(prevFilePath: String, node: TracePointNode) {
        if (prevFilePath == node.tracePoint.tracePath) return

        // Remove the node from the previous node list
        val prevList = this.fileNodesMap[prevFilePath]
        prevList?.remove(node)
        // Add the node to the new file path
        val newFilePath = node.tracePoint.tracePath
        val newList = this.fileNodesMap.getOrPut(newFilePath) { mutableListOf() }
        newList.add(node)
    }


    // Used in mouseClicked event
    fun selectTracePoints(ids: Set<String>) {
        ApplicationManager.getApplication().runReadAction {
            selectedTracePointIds.clear()
            selectedTracePointIds.addAll(ids)
        }
    }

    fun getSelectedTracePointIds(): Set<String> {
      return selectedTracePointIds;
    }

    fun isTracePointSelected(id: String): Boolean = selectedTracePointIds.contains(id)

    fun setExpandedTracePointIds(ids: Set<String>) {
        this.expandedTracePointIds = ids.toMutableSet()
        schedulePersist()
    }


    fun getExpandedTracePointIds(): Set<String> = expandedTracePointIds

    // === Trace Profiles ===

    fun getProfileNames(): List<String> = profiles.map { it.name }

    fun getActiveProfileName(): String = activeProfileName

    fun addProfileListener(listener: () -> Unit) {
        profileListeners.add(listener)
    }

    private fun notifyProfileListeners() {
        profileListeners.forEach { it() }
    }

    private fun syncActiveProfileToStore() {
        val profile = profiles.find { it.name == activeProfileName } ?: return
        profile.tracePointNodes = tracePointNodes
        profile.expandedTracePointIds = expandedTracePointIds.toMutableSet()
    }

    private fun loadActiveProfileFromStore(preserveSelection: Boolean = false) {
        val profile = profiles.find { it.name == activeProfileName }
            ?: profiles.firstOrNull()?.also { activeProfileName = it.name }
            ?: TraceProfile(name = DEFAULT_PROFILE_NAME).also {
                profiles.clear()
                profiles.add(it)
                activeProfileName = it.name
            }
        clearAllHighlights()
        val keepIds = if (preserveSelection) selectedTracePointIds.toSet() else emptySet()
        selectedTracePointIds.clear()
        tracePointNodes = profile.tracePointNodes
        expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
        rebuildNodeMapAndFileNodesMap()
        if (preserveSelection) {
            selectedTracePointIds.addAll(keepIds.filter { nodeMap.containsKey(it) })
        }
        validateTracePointsOnLoad()
        FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
        reattachListenersAndHighlights()
        notifyListeners(restoreSelection = preserveSelection)
    }

    private fun clearAllHighlights() {
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { removeHighlights(it) }
        }
    }

    fun switchProfile(name: String) {
        if (name == activeProfileName || profiles.none { it.name == name }) return
        syncActiveProfileToStore()
        activeProfileName = name
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
    }

    fun addProfile(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || profiles.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return false
        }
        ensureStorage()
        syncActiveProfileToStore()
        profiles.add(TraceProfile(name = trimmed))
        activeProfileName = trimmed
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
        return true
    }

    fun deleteProfile(name: String): Boolean {
        if (profiles.size <= 1) return false
        val index = profiles.indexOfFirst { it.name == name }
        if (index < 0) return false
        profiles.removeAt(index)
        if (activeProfileName == name) {
            activeProfileName = profiles.first().name
            loadActiveProfileFromStore()
        }
        notifyProfileListeners()
        scheduleFullPeerPersist()
        return true
    }

    fun replaceActiveProfileTree(
        nodes: MutableList<TracePointNode>,
        expandedIds: MutableSet<String>
    ) {
        ensureStorage()
        clearAllHighlights()
        selectedTracePointIds.clear()
        tracePointNodes = nodes
        expandedTracePointIds = expandedIds
        rebuildNodeMapAndFileNodesMap()
        validateTracePointsOnLoad()
        FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
        reattachListenersAndHighlights()
        syncActiveProfileToStore()
        markPeerProfileRefresh()
        notifyListeners()
    }

    /** Snapshot of every profile (active tree is synced first). */
    fun getProfilesSnapshot(): List<TraceProfile> {
        syncActiveProfileToStore()
        return profiles.map {
            TraceProfile(
                name = it.name,
                tracePointNodes = it.tracePointNodes,
                expandedTracePointIds = it.expandedTracePointIds.toMutableSet()
            )
        }
    }

    fun allocateUniqueProfileName(desired: String): String {
        val base = desired.trim().ifEmpty { "imported" }
        if (profiles.none { it.name.equals(base, ignoreCase = true) }) return base
        var i = 2
        while (profiles.any { it.name.equals("$base ($i)", ignoreCase = true) }) {
            i++
        }
        return "$base ($i)"
    }

    /**
     * Creates a new profile from imported data and switches to it.
     * @return the actual profile name used (may be renamed on conflict)
     */
    fun importAsNewProfile(
        desiredName: String,
        nodes: MutableList<TracePointNode>,
        expandedIds: MutableSet<String>
    ): String {
        ensureStorage()
        syncActiveProfileToStore()
        val name = allocateUniqueProfileName(desiredName)
        profiles.add(
            TraceProfile(
                name = name,
                tracePointNodes = nodes,
                expandedTracePointIds = expandedIds
            )
        )
        activeProfileName = name
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
        return name
    }

    /**
     * Imports many profiles as new ones (renames on name conflict).
     * Switches to the first imported profile.
     * @return names actually created
     */
    fun importAsNewProfiles(imported: List<TraceProfile>): List<String> {
        if (imported.isEmpty()) return emptyList()
        ensureStorage()
        syncActiveProfileToStore()
        val created = mutableListOf<String>()
        for (profile in imported) {
            val name = allocateUniqueProfileName(profile.name)
            profiles.add(
                TraceProfile(
                    name = name,
                    tracePointNodes = profile.tracePointNodes,
                    expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
                )
            )
            created.add(name)
        }
        activeProfileName = created.first()
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
        return created
    }

    /**
     * Merges imported profiles into local ones.
     * Same-named profiles are overwritten; new names are added.
     * Local-only profiles are kept. Switches to [preferredActiveName] when present.
     */
    fun mergeProfiles(imported: List<TraceProfile>, preferredActiveName: String? = null) {
        if (imported.isEmpty()) return
        ensureStorage()
        syncActiveProfileToStore()
        for (incoming in imported) {
            val existing = profiles.find { it.name.equals(incoming.name, ignoreCase = true) }
            if (existing != null) {
                existing.tracePointNodes = incoming.tracePointNodes
                existing.expandedTracePointIds = incoming.expandedTracePointIds.toMutableSet()
            } else {
                profiles.add(
                    TraceProfile(
                        name = incoming.name,
                        tracePointNodes = incoming.tracePointNodes,
                        expandedTracePointIds = incoming.expandedTracePointIds.toMutableSet()
                    )
                )
            }
        }
        val preferred = preferredActiveName?.takeIf { name -> profiles.any { it.name == name } }
        if (preferred != null) {
            activeProfileName = preferred
        } else if (profiles.none { it.name == activeProfileName }) {
            activeProfileName = profiles.first().name
        }
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
    }

    /**
     * Replaces all local profiles with [imported]. Requires a non-empty list.
     */
    fun replaceAllProfiles(imported: List<TraceProfile>, preferredActiveName: String? = null) {
        if (imported.isEmpty()) return
        ensureStorage()
        profiles = imported.map {
            TraceProfile(
                name = it.name.ifBlank { DEFAULT_PROFILE_NAME },
                tracePointNodes = it.tracePointNodes,
                expandedTracePointIds = it.expandedTracePointIds.toMutableSet()
            )
        }.toMutableList()
        activeProfileName = preferredActiveName
            ?.takeIf { name -> profiles.any { it.name == name } }
            ?: profiles.first().name
        loadActiveProfileFromStore()
        notifyProfileListeners()
        scheduleFullPeerPersist()
    }

    fun addNodeListener(nodeListenerEventType: NodeListenerEventType,
                        listener: (List<TracePointNode>,  Boolean) -> Unit) {
        listenersMap.getOrPut(nodeListenerEventType) { mutableListOf() }.add(listener)
    }

    fun notifyListeners() {
        val copy = getTracePoints()
        val listeners = listenersMap[NodeListenerEventType.FULL_UPDATE]
        listeners?.forEach { it(copy, false) }
        schedulePersist()
    }

    fun notifyListeners(restoreSelection: Boolean) {
        val copy = getTracePoints()
        val listeners = listenersMap[NodeListenerEventType.FULL_UPDATE]
        listeners?.forEach { it(copy, restoreSelection) }
        schedulePersist()
    }

    fun notifyListeners(event: NodeListenerEventType) {
        val copy = getTracePoints()
        val listeners = listenersMap[event]
        listeners?.forEach { it(copy, false) }
        schedulePersist()
    }

    fun notifyListeners(event: NodeListenerEventType, tracePoints: MutableList<TracePointNode>) {
        val listeners = listenersMap[event]
        listeners?.forEach { it(tracePoints, false) }
        schedulePersist()
    }

    private fun reattachListenersAndHighlights() {
        val visitedFiles = mutableSetOf<String>()
        fun traverse(node: TracePointNode) {
            if (node.tracePoint.traceType == TraceType.LINE) {
                val path = node.tracePoint.tracePath
                if (visitedFiles.add(path)) {
                    val file = VirtualFileManager.getInstance()
                        .findFileByUrl("file:///${project.basePath}/$path")
                    file?.let {
                        if (!it.isDirectory) {
                            attachDocumentListener(it)
                            highlightTracePointsInFile(it)
                        }
                    }
                }
            }
            node.children.forEach { traverse(it) }
        }
        getTracePoints().forEach { traverse(it) }
    }

    fun getTraceNodesByFilePath(filePath: String): List<TracePointNode>? {
        return fileNodesMap[filePath]
    }

    fun findValidTracePointsAt(filePath: String, lineNumber: Int): List<TracePointNode> {
        return fileNodesMap[filePath]
            ?.filter {
                it.tracePoint.traceType == TraceType.LINE &&
                    it.tracePoint.isValid &&
                    it.tracePoint.lineNumber == lineNumber
            }
            ?: emptyList()
    }

    @Volatile
    private var treeRevealer: ((Set<String>, Boolean) -> Unit)? = null

    fun setTreeRevealer(revealer: ((Set<String>, Boolean) -> Unit)?) {
        treeRevealer = revealer
    }

    /**
     * Select / reveal nodes in the tool-window tree.
     * @param focusTree when true, moves keyboard focus to the tree (agent go-to); false keeps editor focus (create).
     */
    fun revealTracePointsInTree(ids: Set<String>, focusTree: Boolean = true) {
        if (ids.isEmpty()) return
        selectTracePoints(ids)
        ApplicationManager.getApplication().invokeLater {
            treeRevealer?.invoke(ids, focusTree)
        }
    }
}
