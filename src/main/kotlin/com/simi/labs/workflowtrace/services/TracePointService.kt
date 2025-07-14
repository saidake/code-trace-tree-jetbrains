package com.simi.labs.workflowtrace.services

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
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XCollection
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.util.TextRange
import java.awt.Color
import java.util.*

@Service(Service.Level.PROJECT)
@State(
    name = "TracePointService",
    storages = [Storage("workflowTrace.xml")]
)
class TracePointService(private val project: Project) : PersistentStateComponent<TracePointService.TracePointState> {
    @Tag("TracePoint")
    data class TracePoint(
        @Property val id: String = "",
        @Property val name: String = "",
        @Property val fileName: String = "",
        @Property val lineNumber: Int = 0,
        @Property val parentId: String? = null,
        @Property val projectPath: String = "",
        @Property val lineContent: String? = null,
        @Property val isValid: Boolean = true,
        @Property val totalOccurrenceCount: Int = 0,
        @Property val occurrenceIndex: Int = 0
    ) {
        fun navigateTo(project: Project) {
            ApplicationManager.getApplication().runReadAction {
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///$projectPath/$fileName")
                if (file == null) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Cannot find file: $fileName",
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

    @Tag("TracePointState")
    data class TracePointState(
        @Property @XCollection(elementName = "TracePoint") var tracePoints: List<TracePoint> = emptyList(),
        @Property @XCollection var selectedTracePointIds: List<String> = emptyList(),
        @Property @XCollection var expandedTracePointIds: List<String> = emptyList()
    )

    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()
    private val tracePoints = mutableListOf<TracePoint>()
    private val selectedTracePointIds = mutableSetOf<String>()
    private val expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, DocumentListener>()
    private val highlighters = mutableMapOf<VirtualFile, MutableList<com.intellij.openapi.editor.markup.RangeHighlighter>>()
    private var isFileSystemRefreshing = false

    init {
        // Listen for file openings to attach DocumentListener and apply highlights
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    ApplicationManager.getApplication().runReadAction {
                        monitoredDocuments.remove(file)?.let { listener ->
                            FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(listener)
                        }
                        removeHighlights(file)
                    }
                }
            }
        )

        // Validate trace points on initial load and file refresh
        ApplicationManager.getApplication().runReadAction {
            validateTracePointsOnLoad()
        }

        // Refresh trace points on file system changes
        VirtualFileManager.getInstance().addVirtualFileManagerListener(object : VirtualFileManagerListener {
            override fun beforeRefreshStart(isAsync: Boolean) {
                isFileSystemRefreshing = true
            }

            override fun afterRefreshFinish(isAsync: Boolean) {
                ApplicationManager.getApplication().runReadAction {
                    validateTracePointsOnLoad()
                    isFileSystemRefreshing = false
                }
            }
        }, project)
    }

    private fun highlightTracePointsInFile(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            // Remove existing highlighters for this file
            removeHighlights(file)

            val filePath = file.path.removePrefix(project.basePath + "/")
            val relevantTracePoints = tracePoints.filter { it.fileName == filePath && it.isValid }
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
            val editors = FileEditorManager.getInstance(project).getEditors(file).filterIsInstance<TextEditor>()
            if (editors.isEmpty()) return@runReadAction

            val textAttributes = TextAttributes().apply {
                backgroundColor = Color(255, 255, 200) // Light yellow background
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

    private fun getLineOccurrences(document: com.intellij.openapi.editor.Document, content: String?): Pair<Int, List<Int>> {
        if (content.isNullOrBlank()) return Pair(0, emptyList())
        val lines = document.text.split("\n")
        val matchingLines = lines.mapIndexed { index, line ->
            if (line.trim() == content.trim()) index + 1 else null
        }.filterNotNull()
        return Pair(matchingLines.size, matchingLines)
    }

    private fun attachDocumentListener(file: VirtualFile) {
        ApplicationManager.getApplication().runReadAction {
            val filePath = file.path.removePrefix(project.basePath + "/")
            if (tracePoints.any { it.fileName == filePath } && !monitoredDocuments.containsKey(file)) {
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
                val listener = object : DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        // Skip processing if event is due to file system refresh
                        if (isFileSystemRefreshing) return

                        println("documentChanged triggered")
                        val docFile = FileDocumentManager.getInstance().getFile(event.document) ?: return
                        val docFilePath = docFile.path.removePrefix(project.basePath + "/")
                        val affectedTracePoints = tracePoints.filter { it.fileName == docFilePath }
                        if (affectedTracePoints.isEmpty()) return

                        ApplicationManager.getApplication().runReadAction {
                            val newLines = event.document.text.split("\n")
                            val oldLines = (event.oldFragment.toString().split("\n").size - 1)
                            val newLinesCount = (event.newFragment.toString().split("\n").size - 1)
                            val lineOffset = newLinesCount - oldLines
                            val changedLine = event.document.getLineNumber(event.offset) + 1
                            println("lineOffset: $lineOffset, changedLine: $changedLine")
                            println("oldLines: $oldLines, newLines: $newLines")

                            val updatedTracePoints = tracePoints.map { tracePoint ->
                                if (tracePoint.fileName != docFilePath) return@map tracePoint
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
                                            isValid = true,
                                            totalOccurrenceCount = totalOccurrences,
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
                                            isValid = true,
                                            totalOccurrenceCount = totalOccurrences,
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
                                            totalOccurrenceCount = totalOccurrences,
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
                document.addDocumentListener(listener)
                monitoredDocuments[file] = listener
            }
        }
    }

    private fun validateTracePointsOnLoad() {
        ApplicationManager.getApplication().runReadAction {
            val updatedTracePoints = tracePoints.map { tracePoint ->
                // Invalidate trace points with default/empty required fields
                if (tracePoint.id.isEmpty() || tracePoint.fileName.isEmpty() || tracePoint.projectPath.isEmpty() || tracePoint.lineContent == null) {
                    return@map tracePoint.copy(isValid = false, totalOccurrenceCount = 0, occurrenceIndex = 0)
                }
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${tracePoint.projectPath}/${tracePoint.fileName}")
                if (file == null) {
                    return@map tracePoint.copy(isValid = false, totalOccurrenceCount = 0, occurrenceIndex = 0)
                }
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@map tracePoint.copy(isValid = false, totalOccurrenceCount = 0, occurrenceIndex = 0)
                val lines = document.text.split("\n")
                if (tracePoint.lineNumber <= lines.size) {
                    val currentLineContent = lines[tracePoint.lineNumber - 1].trim()
                    if (currentLineContent == tracePoint.lineContent?.trim()) {
                        // Content matches at the current line number, update occurrence info
                        val (totalOccurrences, matchingLines) = getLineOccurrences(document, tracePoint.lineContent)
                        val occurrenceIndex = matchingLines.indexOf(tracePoint.lineNumber) + 1
                        return@map tracePoint.copy(
                            totalOccurrenceCount = totalOccurrences,
                            occurrenceIndex = occurrenceIndex
                        )
                    }
                }
                // Content does not match at lineNumber, search the file
                val (totalOccurrences, matchingLines) = getLineOccurrences(document, tracePoint.lineContent)
                if (totalOccurrences == tracePoint.totalOccurrenceCount && tracePoint.occurrenceIndex in 1..totalOccurrences) {
                    // Update lineNumber to the line at occurrenceIndex
                    val newLineNumber = matchingLines[tracePoint.occurrenceIndex - 1]
                    return@map tracePoint.copy(
                        lineNumber = newLineNumber,
                        totalOccurrenceCount = totalOccurrences,
                        occurrenceIndex = tracePoint.occurrenceIndex,
                        isValid = true
                    )
                } else {
                    // Mark as invalid if occurrence count differs or occurrenceIndex is out of range
                    return@map tracePoint.copy(isValid = false, totalOccurrenceCount = totalOccurrences, occurrenceIndex = 0)
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

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: Editor?, parentId: String? = null) {
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
                fileName = file.path.removePrefix(project.basePath + "/"),
                lineNumber = lineNumber,
                parentId = parentId,
                projectPath = project.basePath ?: "",
                lineContent = lineContent,
                isValid = document != null && lineContent != null,
                totalOccurrenceCount = totalOccurrences,
                occurrenceIndex = occurrenceIndex
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

    fun renameTracePoint(id: String, newName: String) {
        ApplicationManager.getApplication().runReadAction {
            val index = tracePoints.indexOfFirst { it.id == id }
            if (index >= 0) {
                tracePoints[index] = tracePoints[index].copy(name = newName)
                notifyListeners()
            }
        }
    }

    fun deleteTracePoints(ids: List<String>) {
        ApplicationManager.getApplication().runReadAction {
            // Collect files affected by deleted trace points
            val deletedFiles = tracePoints.filter { it.id in ids }.map { it.fileName }.distinct()
            tracePoints.removeAll { it.id in ids }
            selectedTracePointIds.removeAll(ids)
            expandedTracePointIds.removeAll(ids)

            // Remove highlights for affected files and reapply for remaining trace points
            deletedFiles.forEach { fileName ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$fileName")
                if (file != null) {
                    removeHighlights(file) // Remove all highlights for the file
                    highlightTracePointsInFile(file) // Reapply highlights for remaining trace points
                }
            }

            notifyListeners()
        }
    }

    fun updateTracePoints(newTracePoints: List<TracePoint>) {
        ApplicationManager.getApplication().runReadAction {
            tracePoints.clear()
            tracePoints.addAll(newTracePoints)
            // Re-attach DocumentListeners and reapply highlights
            tracePoints.map { it.fileName }.distinct().forEach { fileName ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$fileName")
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
                expandedTracePointIds = expandedTracePointIds.toList()
            )
        }
    }

    override fun loadState(state: TracePointState) {
        ApplicationManager.getApplication().runReadAction {
            tracePoints.clear()
            tracePoints.addAll(state.tracePoints)
            selectedTracePointIds.clear()
            selectedTracePointIds.addAll(state.selectedTracePointIds)
            expandedTracePointIds.clear()
            expandedTracePointIds.addAll(state.expandedTracePointIds)
            // Re-attach DocumentListeners and apply highlights
            tracePoints.map { it.fileName }.distinct().forEach { fileName ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$fileName")
                if (file != null) {
                    attachDocumentListener(file)
                    highlightTracePointsInFile(file)
                }
            }
            notifyListeners()
        }
    }
}
