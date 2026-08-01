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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.pidifa.codetracetree.domain.enums.NodeListenerEventType
import com.pidifa.codetracetree.domain.enums.TraceType
import com.pidifa.codetracetree.storage.AgentSignalFiles
import com.pidifa.codetracetree.storage.ClaudeAssistTarget
import com.pidifa.codetracetree.storage.ExternalStorageWatcher
import com.pidifa.codetracetree.storage.ProjectDocument
import com.pidifa.codetracetree.storage.ProjectStorage
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
        /** Dedicated profile for Agent Notes when target is [ClaudeAssistTarget.AGENT]. */
        const val AGENT_PROFILE_NAME = "AGENT"
        /** Legacy name; use [AGENT_PROFILE_NAME]. Kept for migration of older storage. */
        const val CLAUDE_PROFILE_NAME = ClaudeAssistTarget.LEGACY_CLAUDE
        private const val SELF_WRITE_IGNORE_MS = 1500L
        private const val EXTERNAL_REBIND_DEBOUNCE_MS = 350L
        private val LOG = Logger.getInstance(TracePointService::class.java)

        /**
         * Renames a legacy `CLAUDE` profile to `AGENT` when needed.
         * @return updated active profile name and whether any rename occurred
         */
        fun migrateClaudeProfileToAgent(
            profiles: MutableList<TraceProfile>,
            activeProfileName: String
        ): Pair<String, Boolean> {
            var changed = false
            var active = activeProfileName
            val agent = profiles.find { it.name.equals(AGENT_PROFILE_NAME, ignoreCase = true) }
            val legacy = profiles.find {
                it.name.equals(ClaudeAssistTarget.LEGACY_CLAUDE, ignoreCase = true)
            }
            when {
                legacy != null && agent == null -> {
                    val wasActive = active.equals(legacy.name, ignoreCase = true)
                    legacy.name = AGENT_PROFILE_NAME
                    if (wasActive) active = AGENT_PROFILE_NAME
                    changed = true
                }
                legacy != null && agent != null -> {
                    if (active.equals(legacy.name, ignoreCase = true)) {
                        active = AGENT_PROFILE_NAME
                        changed = true
                    }
                    if (agent.name != AGENT_PROFILE_NAME) {
                        agent.name = AGENT_PROFILE_NAME
                        changed = true
                    }
                }
                agent != null && agent.name != AGENT_PROFILE_NAME -> {
                    val wasActive = active.equals(agent.name, ignoreCase = true)
                    agent.name = AGENT_PROFILE_NAME
                    if (wasActive) active = AGENT_PROFILE_NAME
                    changed = true
                }
            }
            return active to changed
        }
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
    private var isClaudeAssistEnabled = false
    private var claudeAssistTarget: ClaudeAssistTarget = ClaudeAssistTarget.CURRENT

    private var profiles: MutableList<TraceProfile> = mutableListOf(TraceProfile(name = DEFAULT_PROFILE_NAME))
    private var activeProfileName: String = DEFAULT_PROFILE_NAME
    private var tracePointNodes: MutableList<TracePointNode> = mutableListOf()
    private val nodeMap = mutableMapOf<String, TracePointNode>()
    private val fileNodesMap = mutableMapOf<String, MutableList<TracePointNode>>()
    private var projectStorage: ProjectStorage? = null
    private var persistScheduled = false
    private var suppressPersist = false
    @Volatile
    private var ignoreExternalChangesUntilMs = 0L
    private var externalStorageWatcher: ExternalStorageWatcher? = null
    private val pendingExternalRebindPaths = ConcurrentHashMap.newKeySet<String>()
    private val externalRebindExecutor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "code-trace-tree-rebind-debounce").apply { isDaemon = true }
    }
    @Volatile
    private var externalRebindFuture: ScheduledFuture<*>? = null

    init {
        loadFromHybridStorage()
        startExternalStorageWatcher()

        Disposer.register(project, Disposable {
            externalRebindFuture?.cancel(false)
            externalRebindExecutor.shutdownNow()
            externalStorageWatcher?.close()
            externalStorageWatcher = null
            persistNow()
        })

        // Listen for file openings to attach DocumentListener and apply highlights
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }
            }
        )

        // External disk edits (e.g. Claude) — content rebind when no DocumentListener is active
        project.messageBus.connect(project).subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: List<VFileEvent>) {
                    if (isFileSystemRefreshing) return
                    var scheduled = false
                    for (event in events) {
                        if (event !is VFileContentChangeEvent) continue
                        val file = event.file
                        if (monitoredDocuments.containsKey(file)) continue
                        val relativePath = relativeProjectPath(file) ?: continue
                        val nodes = fileNodesMap[relativePath] ?: continue
                        if (nodes.none { it.tracePoint.traceType == TraceType.LINE }) continue
                        pendingExternalRebindPaths.add(relativePath)
                        scheduled = true
                    }
                    if (scheduled) scheduleExternalContentRebind()
                }
            }
        )

        // Validate trace points on initial load and file refresh
        ApplicationManager.getApplication().runReadAction {
            validateTracePointsOnLoad()
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
            notifyListeners()
        }

        // Refresh trace points on file system changes
        VirtualFileManager.getInstance().addVirtualFileManagerListener(object : VirtualFileManagerListener {
            override fun beforeRefreshStart(isAsync: Boolean) {
                isFileSystemRefreshing = true
            }

            override fun afterRefreshFinish(isAsync: Boolean) {
                ApplicationManager.getApplication().runReadAction {
                    validateTracePointsOnLoad()
                    FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
                    notifyListeners()
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

    private fun scheduleExternalContentRebind() {
        externalRebindFuture?.cancel(false)
        externalRebindFuture = externalRebindExecutor.schedule({
            ApplicationManager.getApplication().invokeLater {
                if (project.isDisposed) return@invokeLater
                val paths = pendingExternalRebindPaths.toSet()
                pendingExternalRebindPaths.clear()
                if (paths.isEmpty()) return@invokeLater
                val changed = rebindLineNodesForPaths(paths)
                if (changed) {
                    FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
                    notifyListeners()
                }
            }
        }, EXTERNAL_REBIND_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
    }

    /**
     * Content-based LINE rebind for external disk edits (Claude / other tools).
     * Does not use DocumentListener offset math.
     */
    private fun rebindLineNodesForPaths(relativePaths: Set<String>): Boolean {
        var changed = false
        ApplicationManager.getApplication().runReadAction {
            for (relativePath in relativePaths) {
                val nodes = fileNodesMap[relativePath] ?: continue
                val basePath = project.basePath ?: continue
                val file = VirtualFileManager.getInstance()
                    .findFileByUrl("file:///$basePath/$relativePath") ?: continue
                val doc = FileDocumentManager.getInstance().getDocument(file) ?: continue
                val lines = doc.text.split("\n")
                for (node in nodes) {
                    if (node.tracePoint.traceType != TraceType.LINE) continue
                    val rebound = rebindLineTracePoint(node.tracePoint, lines)
                    if (rebound != node.tracePoint) {
                        node.tracePoint = rebound
                        changed = true
                    }
                }
            }
        }
        return changed
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
            applyDocument(storage.resolveAndLoad(), validate = false, notifyUi = false)
        } catch (e: Exception) {
            LOG.warn("Code Trace Tree: failed to load hybrid storage; using defaults", e)
        }
    }

    private fun startExternalStorageWatcher() {
        val projectId = projectStorage?.boundProjectId() ?: return
        externalStorageWatcher?.close()
        val watcher = ExternalStorageWatcher(
            projectId = projectId,
            storageFileProvider = { projectStorage?.boundStorageFile() },
            shouldIgnore = { System.currentTimeMillis() < ignoreExternalChangesUntilMs },
            onExternalChange = { reason ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        reloadFromExternalStorage(reason)
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
        watcher.start()
    }

    /**
     * Reloads the bound global XML into memory and refreshes the tool window / highlights.
     * Called when the storage file changes or a global refresh signal is written.
     */
    fun reloadFromExternalStorage(reason: String = "manual"): Boolean {
        val storage = projectStorage ?: return false
        if (System.currentTimeMillis() < ignoreExternalChangesUntilMs) return false
        val doc = storage.reloadBoundDocument() ?: return false
        LOG.info("Code Trace Tree: reloading from external storage ($reason)")
        suppressPersist = true
        var profileMigrated = false
        try {
            profileMigrated = applyDocument(doc, validate = true, notifyUi = true)
        } finally {
            suppressPersist = false
        }
        if (profileMigrated) {
            schedulePersist()
        }
        return true
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

    /** @return true when a legacy `CLAUDE` profile was renamed to `AGENT` */
    private fun applyDocument(doc: ProjectDocument, validate: Boolean, notifyUi: Boolean): Boolean {
        isHighlightingEnabled = doc.highlightingEnabled
        isDescriptionAreaOpened = doc.descriptionAreaOpened
        isNamePromptEnabled = doc.namePromptEnabled
        isClaudeAssistEnabled = doc.claudeAssistEnabled
        claudeAssistTarget = doc.claudeAssistTarget
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
        val (migratedActive, profileMigrated) = migrateClaudeProfileToAgent(profiles, activeProfileName)
        activeProfileName = migratedActive
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
        if (profileMigrated && !suppressPersist) {
            schedulePersist()
        }
        return profileMigrated
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
            claudeAssistEnabled = isClaudeAssistEnabled,
            claudeAssistTarget = claudeAssistTarget
        )
        externalStorageWatcher?.refreshRegistrations()
    }

    // === Highlighting ===

    fun isHighlightingEnabled(): Boolean = isHighlightingEnabled
    fun isDescriptionAreaOpened(): Boolean = isDescriptionAreaOpened
    fun isNamePromptEnabled(): Boolean = isNamePromptEnabled
    fun isClaudeAssistEnabled(): Boolean = isClaudeAssistEnabled
    fun getClaudeAssistTarget(): ClaudeAssistTarget = claudeAssistTarget

    fun setDescriptionAreaOpened(opened: Boolean) {
        isDescriptionAreaOpened = opened
        schedulePersist()
    }

    fun setNamePromptEnabled(enabled: Boolean) {
        isNamePromptEnabled = enabled
        schedulePersist()
    }

    fun setClaudeAssistEnabled(enabled: Boolean) {
        isClaudeAssistEnabled = enabled
        schedulePersist()
    }

    /**
     * Enables Agent Notes (Claude Assist storage flags) and persists the chosen target.
     * For [ClaudeAssistTarget.AGENT], creates/switches to the `AGENT` profile.
     */
    fun enableClaudeAssist(target: ClaudeAssistTarget) {
        claudeAssistTarget = target
        isClaudeAssistEnabled = true
        if (target == ClaudeAssistTarget.AGENT) {
            ensureAgentProfileActive()
        }
        schedulePersist()
    }

    private fun ensureAgentProfileActive() {
        migrateClaudeProfileToAgent(profiles, activeProfileName).let { (migratedActive, _) ->
            activeProfileName = migratedActive
                .takeIf { name -> profiles.any { it.name == name } }
                ?: activeProfileName
        }
        val existing = profiles.find { it.name.equals(AGENT_PROFILE_NAME, ignoreCase = true) }
        if (existing == null) {
            addProfile(AGENT_PROFILE_NAME)
            return
        }
        val wasActive = activeProfileName.equals(existing.name, ignoreCase = true)
        existing.name = AGENT_PROFILE_NAME
        if (wasActive) {
            activeProfileName = AGENT_PROFILE_NAME
            notifyProfileListeners()
        } else {
            switchProfile(AGENT_PROFILE_NAME)
        }
    }

    fun setHighlightingEnabled(enabled: Boolean) {
        isHighlightingEnabled = enabled
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                if (enabled) highlightTracePointsInFile(file) else removeHighlights(file)
            }
        }
        schedulePersist()
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

            // Use different highlight colors based on theme
            val textAttributes = TextAttributes().apply {
                backgroundColor = JBColor(
                    java.awt.Color(255, 255, 200), // Light yellow for light theme
                    java.awt.Color(100, 100, 0)    // Darker yellow for dark theme
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
                            val newLines = event.document.text.split("\n")
                            val oldLinesCount = event.oldFragment.toString().count { it == '\n' }
                            val newLinesCount = event.newFragment.toString().count { it == '\n' }
                            val lineOffset = newLinesCount - oldLinesCount
                            val changedLine = event.document.getLineNumber(event.offset) + 1

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
                    val newContent = newLines.getOrNull(newLineNum - 1)?.trim()
                    val (total, matches) = getLineOccurrences(document, newContent)
                    val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(newLineNum) + 1
                    node.tracePoint = tp.copy(
                        lineNumber = newLineNum,
                        lineContent = newContent,
                        isValid = newContent != null,
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
                tp.lineNumber > changedLine && lineOffset != 0 -> {
                    val newLineNum = (tp.lineNumber + lineOffset).coerceAtLeast(1)
                    val newContent = newLines.getOrNull(newLineNum - 1)?.trim()
                    val (total, matches) = getLineOccurrences(document, newContent)
                    val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(newLineNum) + 1
                    val newTp = tp.copy(
                        lineNumber = newLineNum,
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
            }
        }

        if (updated) updatedNodes.add(node)
        //node.children.forEach { updateNodeRecursively(document, offset,it, filePath, newLines, lineOffset, changedLine, updatedNodes) }
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

                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${basePath}/${tp.tracePath}")
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
    ) {
        ApplicationManager.getApplication().runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(file)
            val lineContent = document?.let {
                val start = it.getLineStartOffset(lineNumber - 1)
                val end = it.getLineEndOffset(lineNumber - 1)
                it.getText(TextRange(start, end)).trim()
            }
            val (totalOccurrences, matchingLines) = if (document != null) {
                getLineOccurrences(document, lineContent)
            } else Pair(0, emptyList())
            val occurrenceIndex = if (lineContent != null) matchingLines.indexOf(lineNumber) + 1 else 0

            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val baseName = file.name

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

            insertTracePointNode(TracePointNode(UUID.randomUUID().toString(), tracePoint), parentId)
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
    ) {
        ApplicationManager.getApplication().runReadAction {
            val relativePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val kind = if (file.isDirectory) TraceType.DIRECTORY else TraceType.FILE
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
            insertTracePointNode(TracePointNode(UUID.randomUUID().toString(), tracePoint), parentId)
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
                schedulePersist()
            }
        }
    }

    fun renameTracePoint(id: String, newName: String) {
        ApplicationManager.getApplication().runReadAction {
            nodeMap[id]?.let {
                it.tracePoint = it.tracePoint.copy(traceName = newName)
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
            notifyListeners()
        }
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

    private fun loadActiveProfileFromStore() {
        val profile = profiles.find { it.name == activeProfileName }
            ?: profiles.firstOrNull()?.also { activeProfileName = it.name }
            ?: TraceProfile(name = DEFAULT_PROFILE_NAME).also {
                profiles.clear()
                profiles.add(it)
                activeProfileName = it.name
            }
        clearAllHighlights()
        selectedTracePointIds.clear()
        tracePointNodes = profile.tracePointNodes
        expandedTracePointIds = profile.expandedTracePointIds.toMutableSet()
        rebuildNodeMapAndFileNodesMap()
        validateTracePointsOnLoad()
        FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
        reattachListenersAndHighlights()
        notifyListeners()
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
    }

    fun addProfile(name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || profiles.any { it.name.equals(trimmed, ignoreCase = true) }) {
            return false
        }
        syncActiveProfileToStore()
        profiles.add(TraceProfile(name = trimmed))
        activeProfileName = trimmed
        loadActiveProfileFromStore()
        notifyProfileListeners()
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
        return true
    }

    fun replaceActiveProfileTree(
        nodes: MutableList<TracePointNode>,
        expandedIds: MutableSet<String>
    ) {
        clearAllHighlights()
        selectedTracePointIds.clear()
        tracePointNodes = nodes
        expandedTracePointIds = expandedIds
        rebuildNodeMapAndFileNodesMap()
        validateTracePointsOnLoad()
        FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
        reattachListenersAndHighlights()
        syncActiveProfileToStore()
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
        return name
    }

    /**
     * Imports many profiles as new ones (renames on name conflict).
     * Switches to the first imported profile.
     * @return names actually created
     */
    fun importAsNewProfiles(imported: List<TraceProfile>): List<String> {
        if (imported.isEmpty()) return emptyList()
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
        return created
    }

    /**
     * Merges imported profiles into local ones.
     * Same-named profiles are overwritten; new names are added.
     * Local-only profiles are kept. Switches to [preferredActiveName] when present.
     */
    fun mergeProfiles(imported: List<TraceProfile>, preferredActiveName: String? = null) {
        if (imported.isEmpty()) return
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
    }

    /**
     * Replaces all local profiles with [imported]. Requires a non-empty list.
     */
    fun replaceAllProfiles(imported: List<TraceProfile>, preferredActiveName: String? = null) {
        if (imported.isEmpty()) return
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
    private var treeRevealer: ((Set<String>) -> Unit)? = null

    fun setTreeRevealer(revealer: ((Set<String>) -> Unit)?) {
        treeRevealer = revealer
    }

    fun revealTracePointsInTree(ids: Set<String>) {
        if (ids.isEmpty()) return
        selectTracePoints(ids)
        ApplicationManager.getApplication().invokeLater {
            treeRevealer?.invoke(ids)
        }
    }
}
