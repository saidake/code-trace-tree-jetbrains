package com.simi.labs.codetracetree.services

import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.JBColor
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.simi.labs.codetracetree.domain.enums.ListenerEventType
import java.util.*

@Service(Service.Level.PROJECT)
@State(
    name = "TracePointService",
    storages = [Storage("code-trace-tree-config.xml")]
)
class TracePointService(private val project: Project) : PersistentStateComponent<TracePointService.TracePointState> {

    @Tag("tracePoint")
    data class TracePoint(
        @Tag("name") val name: String = "",
        @Tag("fileName") val fileName: String = "",
        @Tag("filePath") val filePath: String = "",
        @Tag("lineNumber") val lineNumber: Int = 0,
        @Tag("projectPath") val projectPath: String = "",
        @Tag("lineContent") val lineContent: String? = null,
        @Tag("isValid") val isValid: Boolean = true,
        @Tag("totalOccurrences") val totalOccurrences: Int = 0,
        @Tag("occurrenceIndex") val occurrenceIndex: Int = 0,
        @Tag("description") val description: String = ""
    ) {
        fun navigateTo(project: Project) {
            ApplicationManager.getApplication().runReadAction {
                val fileUrl = "file:///$projectPath/$filePath"
                val file = VirtualFileManager.getInstance().findFileByUrl(fileUrl)
                if (file == null) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Cannot find file: $fileName in project path $projectPath",
                            "Navigate to Trace Point"
                        )
                    }
                    return@runReadAction
                }
                ApplicationManager.getApplication().invokeLater {
                    val editorManager = FileEditorManager.getInstance(project)
                    editorManager.openFile(file, true)
                    val descriptor = OpenFileDescriptor(project, file, lineNumber - 1, 0)
                    descriptor.navigate(true)
                }
            }
        }
    }

    @Tag("tracePointNode")
    data class TracePointNode(
        @Tag("id") val id: String = "",

//        @Tag("tracePoint")
        @Property(surroundWithTag = false)
        var tracePoint: TracePoint= TracePoint(),

        @Tag("parentId")
        var parentId: String? = null,

        @Tag("children")
        @XCollection(elementName = "tracePointNode")
        var children: MutableList<TracePointNode> = mutableListOf()
    )

    @Tag("tracePointState")
    data class TracePointState(
        @Tag("rootNodes")
        @XCollection(elementName = "tracePointNode")
        var rootNodes: MutableList<TracePointNode> = mutableListOf(),

        @Tag("expandedTracePointIds")
        @XCollection(elementName = "id",valueAttributeName = "")
        var expandedTracePointIds: MutableSet<String> = mutableSetOf(),

        @Tag("selectedTracePointIds")
        @XCollection(elementName = "id",valueAttributeName = "")
        var selectedTracePointIds: List<String> = emptyList(),

        @Tag("highlightingEnabled")
        var highlightingEnabled: Boolean = true,

        @Tag("descriptionAreaOpened")
        var descriptionAreaOpened: Boolean = false
    )

    private val listenersMap =mutableMapOf<ListenerEventType, MutableList<(List<TracePointNode>, Set<String>) -> Unit>>()
    private val selectedTracePointIds = mutableSetOf<String>()
    private var expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, DocumentListener>()
    private val highlighters = mutableMapOf<VirtualFile, MutableList<com.intellij.openapi.editor.markup.RangeHighlighter>>()
    private var isFileSystemRefreshing = false
    private var isHighlightingEnabled = true
    private var isDescriptionAreaOpened = false

    private var rootNodes: MutableList<TracePointNode> = mutableListOf()
    private val nodeMap = mutableMapOf<String, TracePointNode>()

    init {
        // Listen for file openings to attach DocumentListener and apply highlights
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    println("TracePointService - fileOpened triggered for file ${file.path}")
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }
            }
        )

        // Validate trace points on initial load and file refresh
        ApplicationManager.getApplication().runReadAction {
            println("validateTracePointsOnLoad triggered in the init method of TracePointService")
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
                    println("validateTracePointsOnLoad triggered in afterRefreshFinish")
                    validateTracePointsOnLoad()
                    FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
                    notifyListeners()
                    isFileSystemRefreshing = false
                }
            }
        }, project)
    }

    // === Highlighting ===

    fun isHighlightingEnabled(): Boolean = isHighlightingEnabled
    fun isDescriptionAreaOpened(): Boolean = isDescriptionAreaOpened
    fun setDescriptionAreaOpened(opened: Boolean) {
        isDescriptionAreaOpened = opened
    }

    fun setHighlightingEnabled(enabled: Boolean) {
        isHighlightingEnabled = enabled
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                if (enabled) highlightTracePointsInFile(file) else removeHighlights(file)
            }
        }
    }

    fun highlightTracePointsInFile(file: VirtualFile) {
        if (!isHighlightingEnabled) return
        ApplicationManager.getApplication().runReadAction {
            // Remove existing highlighters for this file
            removeHighlights(file)

            val filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val relevant = findTracePointNodes { tpNode ->
                tpNode.tracePoint.filePath == filePath && tpNode.tracePoint.isValid
            }
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

    private fun rebuildNodeMaps() {
        nodeMap.clear()
        fun walk(node: TracePointNode) {
            nodeMap[node.id] = node
            node.children.forEach { child ->
                child.parentId = node.id
                walk(child)
            }
        }
        rootNodes.forEach { walk(it) }
    }


    fun attachDocumentListener(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            println("attachDocumentListener triggered")
            val filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val hasTracePointsInFile = anyTracePointNode { it.tracePoint.filePath == filePath }
            if (hasTracePointsInFile && !monitoredDocuments.containsKey(file)) {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
                val listener = object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        // Skip processing if event is due to file system refresh
                        if (isFileSystemRefreshing) return

                        println("documentChanged triggered")
                        val docFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                        val docPath = docFile.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
                        val affected = findTracePointNodes { tpNode ->
                            tpNode.tracePoint.filePath == docPath
                        }
                        if (affected.isEmpty()) return

                        ApplicationManager.getApplication().runReadAction {
                            val newLines = event.document.text.split("\n")
                            val oldLinesCount = event.oldFragment.toString().count { it == '\n' }
                            val newLinesCount = event.newFragment.toString().count { it == '\n' }
                            val lineOffset = newLinesCount - oldLinesCount
                            val changedLine = event.document.getLineNumber(event.offset) + 1

                            rootNodes.forEach { updateNodeRecursively(event.document, it, docPath, newLines, lineOffset, changedLine) }
                            highlightTracePointsInFile(docFile)
                            notifyListeners()
                        }
                    }
                }
                document.addDocumentListener(listener, project)
                monitoredDocuments[file] = listener
            }
        }
    }

    private fun updateNodeRecursively(
        document: com.intellij.openapi.editor.Document,
        node: TracePointNode,
        filePath: String,
        newLines: List<String>,
        lineOffset: Int,
        changedLine: Int
    ) {
        if (node.tracePoint.filePath != filePath) {
            node.children.forEach { updateNodeRecursively(document, it, filePath, newLines, lineOffset, changedLine) }
            return
        }

        val tp = node.tracePoint
        if (!tp.isValid) {
            val valid = newLines.getOrNull(tp.lineNumber - 1)?.trim() == tp.lineContent?.trim()
            node.tracePoint.copy(isValid = valid).also { node.tracePoint = it }
            return
        }

        when {
            tp.lineNumber == changedLine && lineOffset > 0 -> {
                val newLineNum = tp.lineNumber + lineOffset
                val newContent = newLines.getOrNull(newLineNum - 1)?.trim()
                val (total, matches) = getLineOccurrences(
                    document,
                    newContent
                )
                val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(newLineNum) + 1
                node.tracePoint = tp.copy(
                    lineNumber = newLineNum,
                    lineContent = newContent,
                    isValid = newContent != null,
                    totalOccurrences = total,
                    occurrenceIndex = occIdx.coerceAtLeast(0)
                )
            }
            tp.lineNumber == changedLine && lineOffset == 0 -> {
                val newContent = newLines.getOrNull(changedLine - 1)?.trim()
                val (total, matches) = getLineOccurrences(
                    document,
                    newContent
                )
                val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(changedLine) + 1
                node.tracePoint = tp.copy(
                    lineContent = newContent,
                    isValid = newContent != null,
                    totalOccurrences = total,
                    occurrenceIndex = occIdx.coerceAtLeast(0)
                )
            }
            tp.lineNumber > changedLine && lineOffset != 0 -> {
                val newLineNum = (tp.lineNumber + lineOffset).coerceAtLeast(1)
                val newContent = newLines.getOrNull(newLineNum - 1)?.trim()
                val (total, matches) = getLineOccurrences(
                    document,
                    newContent
                )
                val occIdx = if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(newLineNum) + 1
                node.tracePoint = tp.copy(
                    lineNumber = newLineNum,
                    lineContent = newContent,
                    isValid = newContent != null,
                    totalOccurrences = total,
                    occurrenceIndex = occIdx.coerceAtLeast(0)
                )
            }
        }
        node.children.forEach { updateNodeRecursively(document, it, filePath, newLines, lineOffset, changedLine) }
    }

    private fun validateTracePointsOnLoad() {
        ApplicationManager.getApplication().runReadAction {
            fun validateNode(node: TracePointNode) {
                val tp = node.tracePoint
                if (node.id.isEmpty() || tp.filePath.isEmpty() || tp.projectPath.isEmpty() || tp.lineContent == null) {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                    return
                }

                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${tp.projectPath}/${tp.filePath}")
                if (file == null) {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                    return
                }

                val doc = FileDocumentManager.getInstance().getDocument(file) ?: run {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                    return
                }

                val lines = doc.text.split("\n")
                if (tp.lineNumber <= lines.size && lines[tp.lineNumber - 1].trim() == tp.lineContent.trim()) {
                    return
                }

                val (total, matches) = getLineOccurrences(doc, tp.lineContent)
                if (total == tp.totalOccurrences && tp.occurrenceIndex in 1..total) {
                    node.tracePoint = tp.copy(
                        lineNumber = matches[tp.occurrenceIndex - 1],
                        totalOccurrences = total,
                        isValid = true
                    )
                } else {
                    node.tracePoint = tp.copy(isValid = false, totalOccurrences = total, occurrenceIndex = 0)
                }
            }

            rootNodes.forEach { validateRecursively(it, ::validateNode) }
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
        editor: Editor?,
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

            val (totalOccurrences, matchingLines) = if (document != null && lineContent != null) {
                getLineOccurrences(document, lineContent)
            } else Pair(0, emptyList())

            val occurrenceIndex = if (lineContent != null) matchingLines.indexOf(lineNumber) + 1 else 0

            val tp = TracePoint(
                name = name,
                filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: ""),
                fileName = file.name,
                lineNumber = lineNumber,
                projectPath = project.basePath ?: "",
                lineContent = lineContent,
                isValid = document != null && lineContent != null,
                totalOccurrences = totalOccurrences,
                occurrenceIndex = occurrenceIndex,
                description = description
            )

            val newNode = TracePointNode(UUID.randomUUID().toString(), tp)
            if (parentId == null) {
                rootNodes.add(newNode)
            } else {
                nodeMap[parentId]?.children?.add(newNode)?.also { newNode.parentId = nodeMap[parentId]?.id }
            }
            nodeMap[newNode.id] = newNode
        }
    }

    fun updateTracePointDescription(id: String, newDescription: String) {
        ApplicationManager.getApplication().runReadAction {
            nodeMap[id]?.let {
                it.tracePoint = it.tracePoint.copy(description = newDescription)
            }
        }
    }

    fun renameTracePoint(id: String, newName: String) {
        ApplicationManager.getApplication().runReadAction {
            nodeMap[id]?.let {
                it.tracePoint = it.tracePoint.copy(name = newName)
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

            val affectedFiles = toDelete.mapNotNull { nodeMap[it]?.tracePoint?.filePath }.distinct()

            rootNodes.removeIf { toDelete.contains(it.id) }
            rootNodes.forEach { pruneRecursively(it, toDelete) }

            selectedTracePointIds.removeAll(toDelete)
            expandedTracePointIds.removeAll(toDelete)
            rebuildNodeMaps()

            affectedFiles.forEach { path ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$path")
                file?.let {
                    removeHighlights(it)
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

        rootNodes.forEach { walk(it) }
        return result
    }

    fun traverseTracePointNodes(visitor: (TracePointNode) -> TracePointNode) {
        fun walk(node: TracePointNode): TracePointNode {
            val transformedNode = visitor(node)
            node.children.map { walk(it) }
            return transformedNode
        }
        // Transform all root nodes and update the rootNodes collection
        rootNodes = rootNodes.map { walk(it) }.toMutableList()
    }


    fun anyTracePointNode(predicate: (TracePointNode) -> Boolean): Boolean {
        fun walk(node: TracePointNode): Boolean {
            if (predicate(node)) return true
            return node.children.any { walk(it) }
        }
        return rootNodes.any { walk(it) }
    }



    fun updateNodeMap(updatedTracePoints: List<TracePointNode>) {
        ApplicationManager.getApplication().runReadAction {
            for (node in updatedTracePoints) {
                nodeMap[node.id]=node
            }
        }
    }


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

    // === Tree Traversal ===

    fun getTracePoints(): MutableList<TracePointNode>  {
        return this.rootNodes
    }

    fun addRootTracePoint(tracePoint: TracePointNode){
        if(tracePoint.parentId==null)this.rootNodes.add(tracePoint)
    }

    fun removeRootTracePoint(id: String): Boolean {
        val iterator = rootNodes.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().id == id) {
                iterator.remove()
                return true
            }
        }
        return false
    }

    fun addRootTracePointNextTo(tracePoint: TracePointNode, id: String) {
        if (tracePoint.parentId != null) return
        val index = rootNodes.indexOfFirst { it.id == id }
        if (index != -1) {
            rootNodes.add(index + 1, tracePoint)
        } else {
            rootNodes.add(tracePoint)
        }
    }



    fun getTracePointById( id: String): TracePointNode?  {
        return this.nodeMap[id]
    }


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
        this.expandedTracePointIds=ids.toMutableSet()
    }


    fun getExpandedTracePointIds(): Set<String> = expandedTracePointIds

    fun addTracePointListener(listenerEventType: ListenerEventType, listener: (List<TracePointNode>, Set<String>) -> Unit) {
        listenersMap.getOrPut(listenerEventType) { mutableListOf() }.add(listener)
        //listener(getTracePoints(), expandedTracePointIds)
    }

    fun notifyListeners() {
        println("notifyListeners triggered")
        val copy = getTracePoints()
        val exp = expandedTracePointIds
        val listeners= listenersMap[ListenerEventType.ALL]
        listeners?.forEach { it(copy, exp) }
    }

    fun notifyListeners(event: ListenerEventType) {
        println("notifyListeners triggered: $event")
        val copy = getTracePoints()
        val exp = expandedTracePointIds
        val listeners= listenersMap[event]
        listeners?.forEach { it(copy, exp) }
    }

    override fun getState(): TracePointState = TracePointState(
        rootNodes = rootNodes,
        expandedTracePointIds = expandedTracePointIds,
        selectedTracePointIds = selectedTracePointIds.toList(),
        highlightingEnabled = isHighlightingEnabled,
        descriptionAreaOpened = isDescriptionAreaOpened
    )

    override fun loadState(state: TracePointState) {
        println("loadState triggered")
        ApplicationManager.getApplication().runReadAction {
            rootNodes.clear()
            rootNodes.addAll(state.rootNodes)
            rebuildNodeMaps()
            selectedTracePointIds.clear(); selectedTracePointIds.addAll(state.selectedTracePointIds)
            expandedTracePointIds.clear(); expandedTracePointIds.addAll(state.expandedTracePointIds)
            isHighlightingEnabled = state.highlightingEnabled
            isDescriptionAreaOpened = state.descriptionAreaOpened
            validateTracePointsOnLoad()
            FileEditorManager.getInstance(project).openFiles.forEach { highlightTracePointsInFile(it) }
            reattachListenersAndHighlights()
            notifyListeners(ListenerEventType.INIT)
            notifyListeners()
        }
    }

    private fun reattachListenersAndHighlights() {
        val visitedFiles = mutableSetOf<String>()
        fun traverse(node: TracePointNode) {
            val path = node.tracePoint.filePath
            if (visitedFiles.add(path)) {
                val file = VirtualFileManager.getInstance()
                    .findFileByUrl("file:///${project.basePath}/$path")
                file?.let {
                    attachDocumentListener(it)
                    highlightTracePointsInFile(it)
                }
            }
            node.children.forEach { traverse(it) }
        }
        getTracePoints().forEach { traverse(it) }
    }
}