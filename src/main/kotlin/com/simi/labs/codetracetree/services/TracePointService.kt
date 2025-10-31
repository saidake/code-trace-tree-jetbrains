package com.simi.labs.codetracetree.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFileManagerListener
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.util.TextRange
import com.intellij.ui.JBColor
import java.util.*

@Service(Service.Level.PROJECT)
@State(
    name = "TracePointService",
    storages = [Storage("code-trace-tree-config.xml")]
)
class TracePointService(private val project: Project) : PersistentStateComponent<TracePointService.TracePointState> {
    @Tag("tracePoint")
    data class TracePoint(
        @Tag("id") val id: String = "",
        @Tag("name") val name: String = "",
        @Tag("fileName") val fileName: String = "",
        @Tag("filePath") val filePath: String = "",

        @Tag("lineNumber") val lineNumber: Int = 0,

        @Tag("parentId") val parentId: String? = null,

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

    @Tag("tracePointState")
    data class TracePointState(
        @Tag("tracePoints")
        @XCollection(elementName = "tracePoint") var tracePoints: List<TracePoint> = emptyList(),
        @Tag("expandedTracePointIds")
        @XCollection(elementName = "id") var expandedTracePointIds: List<String> = emptyList(),

        @Tag("selectedTracePointIds")
        @XCollection(elementName = "id") var selectedTracePointIds: List<String> = emptyList(),

        @Tag("highlightingEnabled")
        var highlightingEnabled: Boolean = true,
        @Tag("descriptionAreaOpened")
        var descriptionAreaOpened: Boolean = false
    )

    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()
    private val tracePoints = mutableListOf<TracePoint>()
    private var tracePointMap: MutableMap<String, TracePoint> = mutableMapOf()
    private val selectedTracePointIds = mutableSetOf<String>()
    private val expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, DocumentListener>()
    private val highlighters = mutableMapOf<VirtualFile, MutableList<com.intellij.openapi.editor.markup.RangeHighlighter>>()
    private var isFileSystemRefreshing = false
    private var isHighlightingEnabled = true
    private var isDescriptionAreaOpened = false

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
                    isFileSystemRefreshing = false
                }
            }
        }, project)
    }

    fun isHighlightingEnabled(): Boolean {
        return isHighlightingEnabled
    }
    fun isDescriptionAreaOpened(): Boolean {
        return isDescriptionAreaOpened
    }
    fun setDescriptionAreaOpened(opened: Boolean) {
        isDescriptionAreaOpened=opened
    }

    fun setHighlightingEnabled(enabled: Boolean) {
        isHighlightingEnabled = enabled
        ApplicationManager.getApplication().runReadAction {
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                if (enabled) {
                    highlightTracePointsInFile(file)
                } else {
                    removeHighlights(file)
                }
            }
        }
    }

    fun highlightTracePointsInFile(file: VirtualFile) {
        if (!isHighlightingEnabled) return
        ApplicationManager.getApplication().runReadAction {
            // Remove existing highlighters for this file
            removeHighlights(file)

            val filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            val relevantTracePoints = tracePoints.filter { it.filePath == filePath && it.isValid }
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
            for (tracePoint in relevantTracePoints) {
                if (tracePoint.lineNumber <= document.lineCount) {
                    val startOffset = document.getLineStartOffset(tracePoint.lineNumber - 1)
                    val endOffset = document.getLineEndOffset(tracePoint.lineNumber - 1)
                    editors.forEach { textEditor ->
                        val editor = textEditor.editor
                        val highlighter = editor.markupModel.addRangeHighlighter(
                            startOffset,
                            endOffset,
                            HighlighterLayer.SELECTION - 1,
                            textAttributes,
                            HighlighterTargetArea.LINES_IN_RANGE
                        )
                        newHighlighters.add(highlighter)
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
        val matchingLines = lines.mapIndexed { index, line ->
            if (line.trim() == content.trim()) index + 1 else null
        }.filterNotNull()
        return Pair(matchingLines.size, matchingLines)
    }

    fun updateTracePointMap() {
        tracePointMap = tracePoints.associateBy { it.id }.toMutableMap()
    }

    private fun attachDocumentListener(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            println("attachDocumentListener triggered")
            val filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
            if (tracePoints.any { it.filePath == filePath } && !monitoredDocuments.containsKey(file)) {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
                val listener = object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        // Skip processing if event is due to file system refresh
                        if (isFileSystemRefreshing) return

                        println("documentChanged triggered")
                        val docFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                        val docFilePath = docFile.path.removePrefix(project.basePath?.let { "$it/" } ?: "")
                        val affectedTracePoints = tracePoints.filter { it.filePath == docFilePath }
                        if (affectedTracePoints.isEmpty()) return

                        ApplicationManager.getApplication().runReadAction {
                            val newLines = event.document.text.split("\n")
                            val oldLines = (event.oldFragment.toString().split("\n").size - 1)
                            val newLinesCount = (event.newFragment.toString().split("\n").size - 1)
                            val lineOffset = newLinesCount - oldLines
                            val changedLine = event.document.getLineNumber(event.offset) + 1
                            // println("lineOffset: $lineOffset, changedLine: $changedLine")
                            // println("oldLines: $oldLines, newLines: $newLines")

                            val updatedTracePoints = tracePoints.map { tracePoint ->
                                if (tracePoint.filePath != docFilePath) return@map tracePoint
                                // Revalidate invalid line
                                if (!tracePoint.isValid) {
                                    return@map tracePoint.copy(
                                        isValid = newLines[tracePoint.lineNumber-1] == tracePoint.lineContent
                                    )
                                }
                                when {
                                    // Update line number and content for trace points at the changed line if lines were added
                                    tracePoint.lineNumber == changedLine && lineOffset > 0 -> {
                                        val newLineNumber = tracePoint.lineNumber + lineOffset
                                        val newContent = if (newLineNumber <= newLines.size) {
                                            val startOffset = event.document.getLineStartOffset(newLineNumber - 1)
                                            val endOffset = event.document.getLineEndOffset(newLineNumber - 1)
                                            event.document.getText(TextRange(startOffset, endOffset)).trim()
                                        } else {
                                            null
                                        }
                                        val (totalOccurrences, matchingLines) = getLineOccurrences(event.document, newContent)
                                        val newOccurrenceIndex = if (newContent == tracePoint.lineContent) {
                                            tracePoint.occurrenceIndex
                                        } else {
                                            matchingLines.indexOf(newLineNumber) + 1
                                        }
                                        tracePoint.copy(
                                            lineNumber = newLineNumber,
                                            lineContent = newContent,
                                            isValid = newContent != null,
                                            totalOccurrences = totalOccurrences,
                                            occurrenceIndex = newOccurrenceIndex
                                        )
                                    }
                                    // Update line content if changed at trace point's line (no line insertion/deletion)
                                    tracePoint.lineNumber == changedLine && lineOffset == 0 -> {
                                        val newContent = if (changedLine <= newLines.size) {
                                            val startOffset = event.document.getLineStartOffset(changedLine - 1)
                                            val endOffset = event.document.getLineEndOffset(changedLine - 1)
                                            event.document.getText(TextRange(startOffset, endOffset)).trim()
                                        } else {
                                            null
                                        }
                                        val (totalOccurrences, matchingLines) = getLineOccurrences(event.document, newContent)
                                        val newOccurrenceIndex = if (newContent == tracePoint.lineContent) {
                                            tracePoint.occurrenceIndex
                                        } else {
                                            matchingLines.indexOf(changedLine) + 1
                                        }
                                        tracePoint.copy(
                                            lineContent = newContent,
                                            isValid = newContent != null,
                                            totalOccurrences = totalOccurrences,
                                            occurrenceIndex = newOccurrenceIndex
                                        )
                                    }
                                    // Adjust line number if change is above trace point
                                    tracePoint.lineNumber > changedLine && lineOffset != 0 -> {
                                        val newLineNumber = (tracePoint.lineNumber + lineOffset).coerceAtLeast(1)
                                        val newContent = if (newLineNumber <= newLines.size) {
                                            val startOffset = event.document.getLineStartOffset(newLineNumber - 1)
                                            val endOffset = event.document.getLineEndOffset(newLineNumber - 1)
                                            event.document.getText(TextRange(startOffset, endOffset)).trim()
                                        } else {
                                            null
                                        }
                                        val (totalOccurrences, matchingLines) = getLineOccurrences(event.document, newContent)
                                        val newOccurrenceIndex = if (newContent == tracePoint.lineContent) {
                                            tracePoint.occurrenceIndex
                                        } else {
                                            matchingLines.indexOf(newLineNumber) + 1
                                        }
                                        tracePoint.copy(
                                            lineNumber = newLineNumber,
                                            lineContent = newContent,
                                            isValid = newContent != null,
                                            totalOccurrences = totalOccurrences,
                                            occurrenceIndex = newOccurrenceIndex
                                        )
                                    }
                                    else -> tracePoint
                                }
                            }
                            tracePoints.clear()
                            tracePoints.addAll(updatedTracePoints)
                            // Reapply highlights for the updated file
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

    private fun validateTracePointsOnLoad() {
        ApplicationManager.getApplication().runReadAction {
            println("validateTracePointsOnLoad triggered")
            val updatedTracePoints = tracePoints.map { tracePoint ->
                // Invalidate trace points with default/empty required fields
                if (tracePoint.id.isEmpty() || tracePoint.filePath.isEmpty() || tracePoint.projectPath.isEmpty() || tracePoint.lineContent == null) {
                    return@map tracePoint.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                }
                // The target file doesn't exist.
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${tracePoint.projectPath}/${tracePoint.filePath}")
                if (file == null) {
                    return@map tracePoint.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
                }
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@map tracePoint.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)

                // Invalid line number
                val lines = document.text.split("\n")
                if (tracePoint.lineNumber <= lines.size) {
                    val currentLineContent = lines[tracePoint.lineNumber - 1].trim()
                    if (currentLineContent == tracePoint.lineContent.trim()) {
                        return@map tracePoint
                    }
                }

                // Content does not match at lineNumber, search the file
                val (totalOccurrences, matchingLines) = getLineOccurrences(document, tracePoint.lineContent)
                println("occurrence doesn't match: totalOccurrences: $totalOccurrences, matchingLines: $matchingLines, tracePoint.totalOccurrenceCount: ${tracePoint.totalOccurrences} tracePoint.occurrenceIndex: ${tracePoint.occurrenceIndex}")
                if (totalOccurrences == tracePoint.totalOccurrences && tracePoint.occurrenceIndex in 1..totalOccurrences) {
                    // Update lineNumber to the line at occurrenceIndex
                    val newLineNumber = matchingLines[tracePoint.occurrenceIndex - 1]

                    return@map tracePoint.copy(
                        lineNumber = newLineNumber,
                        totalOccurrences = totalOccurrences,
                        occurrenceIndex = tracePoint.occurrenceIndex,
                        isValid = true
                    )
                } else {
                    // Mark as invalid if occurrence count differs or occurrenceIndex is out of range
                    return@map tracePoint.copy(isValid = false, totalOccurrences = totalOccurrences, occurrenceIndex = 0)
                }
            }
            tracePoints.clear()
            tracePoints.addAll(updatedTracePoints)
            // Reapply highlights for all open files
            FileEditorManager.getInstance(project).openFiles.forEach { file ->
                highlightTracePointsInFile(file)
            }
            notifyListeners()
        }
    }

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: Editor?, parentId: String? = null, description: String = "") {
        println("TracePointService - addTracePoint triggered")
        ApplicationManager.getApplication().runReadAction {
            val document = FileDocumentManager.getInstance().getDocument(file)
            val lineContent = if (document != null && lineNumber <= document.lineCount) {
                val startOffset = document.getLineStartOffset(lineNumber - 1)
                val endOffset = document.getLineEndOffset(lineNumber - 1)
                document.getText(TextRange(startOffset, endOffset)).trim()
            } else {
                null
            }
            val (totalOccurrences, matchingLines) = if (document != null && lineContent != null) {
                getLineOccurrences(document, lineContent)
            } else {
                Pair(0, emptyList())
            }
            val occurrenceIndex = if (lineContent != null) matchingLines.indexOf(lineNumber) + 1 else 0
            val tracePoint = TracePoint(
                id = UUID.randomUUID().toString(),
                name = name,
                filePath = file.path.removePrefix(project.basePath?.let { "$it/" } ?: ""),
                fileName = file.name,
                lineNumber = lineNumber,
                parentId = parentId,
                projectPath = project.basePath ?: "",
                lineContent = lineContent,
                isValid = document != null && lineContent != null,
                totalOccurrences = totalOccurrences,
                occurrenceIndex = occurrenceIndex,
                description = description
            )
            tracePoints.add(tracePoint)
            // Attach DocumentListener and highlight for the file
            if (document != null) {
                attachDocumentListener(file)
                highlightTracePointsInFile(file)
            }
            notifyListeners()
        }
    }

    fun updateTracePointDescription(id: String, newDescription: String) {
        ApplicationManager.getApplication().runReadAction {
            val index = tracePoints.indexOfFirst { it.id == id }
            if (index >= 0) {
                tracePoints[index] = tracePoints[index].copy(description = newDescription)
                notifyListeners()
            }
        }
    }

    fun renameTracePoint(id: String, newName: String) {
        ApplicationManager.getApplication().runReadAction {
            val index = tracePoints.indexOfFirst { it.id == id }
            if (index >= 0) {
                tracePoints[index] = tracePoints[index].copy(name = newName)
                notifyListeners()
            }
        }
    }

    fun deleteTracePointsWithChildren(ids: List<String>) {
        println("deleteTracePointsWithChildren triggered, ids: $ids")
        ApplicationManager.getApplication().runReadAction {
            // Collect all IDs to delete (target IDs + all their descendants)
            val idsToDelete = mutableSetOf<String>()
            idsToDelete.addAll(ids)

            // Recursively collect all descendant IDs
            fun collectDescendants(currentIds: Set<String>) {
                val newDescendants = tracePoints
                    .filter { it.parentId in currentIds }
                    .map { it.id }
                    .toSet()
                if (newDescendants.isNotEmpty()) {
                    idsToDelete.addAll(newDescendants)
                    collectDescendants(newDescendants)
                }
            }

            collectDescendants(ids.toSet())

            println("Deleting ${idsToDelete.size} trace points (including descendants)")

            // Collect files affected by deleted trace points
            val deletedFiles = tracePoints
                .filter { it.id in idsToDelete }
                .map { it.filePath }
                .distinct()

            // Remove trace points
            tracePoints.removeAll { it.id in idsToDelete }

            // Clean up selections and expansions
            selectedTracePointIds.removeAll(idsToDelete)
            expandedTracePointIds.removeAll(idsToDelete)

            // Remove highlights for affected files and reapply for remaining trace points
            deletedFiles.forEach { filePath ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$filePath")
                if (file != null) {
                    removeHighlights(file) // Remove all highlights for the file
                    highlightTracePointsInFile(file) // Reapply highlights for remaining trace points
                }
            }

            notifyListeners()
        }
    }

    fun updateTracePoints(newTracePoints: List<TracePoint>) {
        println("TracePointService - updateTracePoints triggered")
        ApplicationManager.getApplication().runReadAction {
            tracePoints.clear()
            tracePoints.addAll(newTracePoints)
            // Re-attach DocumentListeners and reapply highlights
            tracePoints.map { it.filePath }.distinct().forEach { filePath ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$filePath")
                if (file != null) {
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }
            }
            notifyListeners()
        }
    }

    fun getTracePoints(): List<TracePoint> {
        return ApplicationManager.getApplication().runReadAction<List<TracePoint>> { tracePoints.toList() }
    }

    fun selectTracePoints(ids: List<String>) {
        ApplicationManager.getApplication().runReadAction {
            selectedTracePointIds.clear()
            selectedTracePointIds.addAll(ids)
            notifyListeners()
        }
    }

    fun toggleTracePointSelection(id: String) {
        ApplicationManager.getApplication().runReadAction {
            if (!selectedTracePointIds.remove(id)) {
                selectedTracePointIds.add(id)
            }
            notifyListeners()
        }
    }

    fun isTracePointSelected(id: String): Boolean {
        return ApplicationManager.getApplication().runReadAction<Boolean> { selectedTracePointIds.contains(id) }
    }

    fun setExpandedTracePointIds(ids: List<String>) {
        ApplicationManager.getApplication().runReadAction {
            expandedTracePointIds.clear()
            expandedTracePointIds.addAll(ids)
            notifyListeners()
        }
    }

    fun getExpandedTracePointIds(): List<String> {
        return ApplicationManager.getApplication().runReadAction<List<String>> { expandedTracePointIds.toList() }
    }

    fun addTracePointListener(listener: (List<TracePoint>, List<String>) -> Unit) {
        ApplicationManager.getApplication().runReadAction {
            listeners.add(listener)
            listener(tracePoints, expandedTracePointIds.toList())
        }
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication().runReadAction {
            val tracePointsCopy = tracePoints.toList()
            val expandedIdsCopy = expandedTracePointIds.toList()
            listeners.forEach { it(tracePointsCopy, expandedIdsCopy) }
        }
    }

    override fun getState(): TracePointState {
        return ApplicationManager.getApplication().runReadAction<TracePointState> {
            TracePointState(
                tracePoints = tracePoints.toList(),
                selectedTracePointIds = selectedTracePointIds.toList(),
                expandedTracePointIds = expandedTracePointIds.toList(),
                highlightingEnabled = isHighlightingEnabled,
                descriptionAreaOpened = isDescriptionAreaOpened
            )
        }
    }

    override fun loadState(state: TracePointState) {
        println("TracePointService - loadState triggered")
        ApplicationManager.getApplication().runReadAction {
            tracePoints.clear()
            tracePoints.addAll(state.tracePoints)
            selectedTracePointIds.clear()
            selectedTracePointIds.addAll(state.selectedTracePointIds)
            expandedTracePointIds.clear()
            expandedTracePointIds.addAll(state.expandedTracePointIds)
            isHighlightingEnabled = state.highlightingEnabled
            isDescriptionAreaOpened = state.descriptionAreaOpened
            println("validateTracePointsOnLoad triggered in loadState")
            validateTracePointsOnLoad()

            // Re-attach DocumentListeners and apply highlights
            tracePoints.map { it.filePath }.distinct().forEach { filePath ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$filePath")
                if (file != null) {
                    println("TracePointService - file validation triggered: ${file.path}")
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }
            }
            notifyListeners()
        }
    }
}