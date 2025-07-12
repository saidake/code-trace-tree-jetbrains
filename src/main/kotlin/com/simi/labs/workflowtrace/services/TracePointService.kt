package com.simi.labs.workflowtrace.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.XCollection
import java.awt.Color
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(
    name = "WorkflowTraceState",
    storages = [Storage("workflowTrace.xml")]
)
class TracePointService(private val project: Project) : PersistentStateComponent<TracePointService.State> {
    data class TracePoint(
        val id: String,
        var name: String,
        val file: VirtualFile,
        var lineNumber: Int,
        val project: Project,
        val lineContent: String,
        var isValid: Boolean = true,
        val parentId: String? = null
    ) {
        val fileName: String get() = file.name
        fun navigateTo() {
            println("Navigating to trace point: $name in ${file.name} at line $lineNumber")
            if (!file.isValid) {
                thisLogger().warn("VirtualFile is invalid: ${file.name}")
                return
            }
            ApplicationManager.getApplication().invokeLater {
                try {
                    file.refresh(false, false)
                    FileEditorManager.getInstance(project).openFile(file, true)
                    val editor = FileEditorManager.getInstance(project).selectedTextEditor
                    if (editor == null) {
                        thisLogger().warn("No editor found for file: ${file.name}")
                        return@invokeLater
                    }
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile == null) {
                        thisLogger().warn("PsiFile not found for: ${file.name}")
                        return@invokeLater
                    }
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    if (document == null) {
                        thisLogger().warn("Document not found for PsiFile: ${file.name}")
                        return@invokeLater
                    }
                    val lineCount = document.lineCount
                    if (lineNumber < 1 || lineNumber > lineCount) {
                        thisLogger().warn("Invalid line number $lineNumber for file ${file.name} (line count: $lineCount)")
                        return@invokeLater
                    }
                    val offset = document.getLineStartOffset(lineNumber - 1)
                    editor.caretModel.moveToOffset(offset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    println("Navigation successful to offset $offset in ${file.name}")
                } catch (e: Exception) {
                    thisLogger().error("Error navigating to trace point: ${e.message}", e)
                }
            }
        }
    }

    data class State(
        @XCollection(elementTypes = [TracePointData::class])
        val tracePoints: MutableList<TracePointData> = mutableListOf(),
        @XCollection
        val expandedTracePointIds: MutableList<String> = mutableListOf()
    )

    data class TracePointData(
        @Attribute var id: String = "",
        @Attribute var name: String = "",
        @Attribute var filePath: String = "",
        @Attribute var lineNumber: Int = 0,
        @Attribute var lineContent: String = "",
        @Attribute var parentId: String? = null
    )

    private val tracePoints = mutableListOf<TracePoint>()
    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()
    private val highlighters = mutableMapOf<String, RangeHighlighter>()
    private val selectedTracePoints = mutableSetOf<String>()
    private val expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, com.intellij.openapi.editor.Document>()

    init {
        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                if (tracePoints.any { it.file == file }) {
                    val document = FileDocumentManager.getInstance().getDocument(file)
                    if (document != null && !monitoredDocuments.containsKey(file)) {
                        setupDocumentListener(file, document)
                    }
                }
            }

            override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                monitoredDocuments.remove(file)
            }
        })
    }

    private fun setupDocumentListener(file: VirtualFile, document: com.intellij.openapi.editor.Document) {
        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                updateTracePointsForFile(file)
            }
        })
        monitoredDocuments[file] = document
        println("Added document listener for file: ${file.name}")
    }

    private fun updateTracePointsForFile(file: VirtualFile) {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return
        val affectedTracePoints = tracePoints.filter { it.file == file }
        if (affectedTracePoints.isEmpty()) return

        affectedTracePoints.forEach { tracePoint ->
            val index = tracePoints.indexOf(tracePoint)
            if (tracePoint.lineNumber <= document.lineCount) {
                val lineStartOffset = document.getLineStartOffset(tracePoint.lineNumber - 1)
                val lineEndOffset = document.getLineEndOffset(tracePoint.lineNumber - 1)
                val currentLineContent = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset)).trimEnd()
                if (currentLineContent != tracePoint.lineContent) {
                    val matchingLines = mutableListOf<Int>()
                    for (line in 0 until document.lineCount) {
                        val startOffset = document.getLineStartOffset(line)
                        val endOffset = document.getLineEndOffset(line)
                        val lineText = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset)).trimEnd()
                        if (lineText == tracePoint.lineContent) {
                            matchingLines.add(line + 1)
                        }
                    }
                    when (matchingLines.size) {
                        1 -> {
                            val newTracePoint = tracePoint.copy(lineNumber = matchingLines[0], isValid = true)
                            tracePoints[index] = newTracePoint
                            highlighters[tracePoint.id]?.let { highlighter ->
                                FileEditorManager.getInstance(project).openTextEditor(
                                    com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                                )?.let { editor ->
                                    editor.markupModel.removeHighlighter(highlighter)
                                    val textAttributes = TextAttributes()
                                    textAttributes.backgroundColor = Color.YELLOW
                                    val newHighlighter = editor.markupModel.addLineHighlighter(
                                        matchingLines[0] - 1,
                                        HighlighterLayer.WARNING,
                                        textAttributes
                                    )
                                    highlighters[tracePoint.id] = newHighlighter
                                }
                            }
                            println("Updated line number for trace point ${tracePoint.name} to ${matchingLines[0]} in ${file.name}")
                        }
                        else -> {
                            val newTracePoint = tracePoint.copy(isValid = false)
                            tracePoints[index] = newTracePoint
                            highlighters[tracePoint.id]?.let { highlighter ->
                                FileEditorManager.getInstance(project).openTextEditor(
                                    com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                                )?.let { editor ->
                                    editor.markupModel.removeHighlighter(highlighter)
                                }
                                highlighters.remove(tracePoint.id)
                            }
                            thisLogger().warn("Trace point ${tracePoint.name} in ${file.name} is invalid: ${matchingLines.size} matches found for line content '${tracePoint.lineContent}'")
                        }
                    }
                } else {
                    if (!tracePoint.isValid) {
                        val newTracePoint = tracePoint.copy(isValid = true)
                        tracePoints[index] = newTracePoint
                        if (!highlighters.containsKey(tracePoint.id)) {
                            FileEditorManager.getInstance(project).openTextEditor(
                                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                            )?.let { editor ->
                                val textAttributes = TextAttributes()
                                textAttributes.backgroundColor = Color.YELLOW
                                val highlighter = editor.markupModel.addLineHighlighter(
                                    tracePoint.lineNumber - 1,
                                    HighlighterLayer.WARNING,
                                    textAttributes
                                )
                                highlighters[tracePoint.id] = highlighter
                            }
                        }
                        println("Restored validity for trace point ${tracePoint.name} in ${file.name}")
                    }
                }
            } else {
                val newTracePoint = tracePoint.copy(isValid = false)
                tracePoints[index] = newTracePoint
                highlighters[tracePoint.id]?.let { highlighter ->
                    FileEditorManager.getInstance(project).openTextEditor(
                        com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                    )?.let { editor ->
                        editor.markupModel.removeHighlighter(highlighter)
                    }
                    highlighters.remove(tracePoint.id)
                }
                thisLogger().warn("Trace point ${tracePoint.name} in ${file.name} is invalid: line number ${tracePoint.lineNumber} exceeds document line count ${document.lineCount}")
            }
        }
        notifyListeners()
    }

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: Editor, parentId: String? = null) {
        val document = editor.document
        val lineContent = if (lineNumber <= document.lineCount) {
            val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
            val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
            document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset)).trimEnd()
        } else {
            ""
        }
        val tracePoint = TracePoint(UUID.randomUUID().toString(), name, file, lineNumber, project, lineContent, parentId = parentId)
        tracePoints.add(tracePoint)

        val textAttributes = TextAttributes()
        textAttributes.backgroundColor = Color.YELLOW
        val highlighter = editor.markupModel.addLineHighlighter(lineNumber - 1, HighlighterLayer.WARNING, textAttributes)
        highlighters[tracePoint.id] = highlighter

        if (!monitoredDocuments.containsKey(file)) {
            val doc = FileDocumentManager.getInstance().getDocument(file)
            if (doc != null) {
                setupDocumentListener(file, doc)
            }
        }

        println("Added trace point: $name in ${file.name} at line $lineNumber with content '$lineContent' and parentId $parentId")
        notifyListeners()
    }

    fun renameTracePoint(tracePointId: String, newName: String) {
        val tracePoint = tracePoints.find { it.id == tracePointId }
        if (tracePoint != null) {
            tracePoint.name = newName
            println("Renamed trace point ${tracePoint.id} to $newName")
            notifyListeners()
        } else {
            thisLogger().warn("Trace point with ID $tracePointId not found for renaming")
        }
    }

    fun updateTracePoints(tracePointIdNamePairs: List<Pair<String, String>>, newFile: VirtualFile, newLineNumber: Int, editor: Editor) {
        val document = editor.document
        val lineContent = if (newLineNumber <= document.lineCount) {
            val lineStartOffset = document.getLineStartOffset(newLineNumber - 1)
            val lineEndOffset = document.getLineEndOffset(newLineNumber - 1)
            document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset)).trimEnd()
        } else {
            ""
        }
        tracePointIdNamePairs.forEach { (id, name) ->
            val tracePoint = tracePoints.find { it.id == id }
            if (tracePoint != null) {
                highlighters[id]?.let { highlighter ->
                    val psiFile = PsiManager.getInstance(project).findFile(tracePoint.file)
                    if (psiFile != null) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                        if (document != null) {
                            FileEditorManager.getInstance(project).openTextEditor(
                                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, tracePoint.file), false
                            )?.let { openEditor ->
                                openEditor.markupModel.removeHighlighter(highlighter)
                            }
                        }
                    }
                    highlighters.remove(id)
                }
                val newTracePoint = TracePoint(tracePoint.id, name, newFile, newLineNumber, project, lineContent, parentId = tracePoint.parentId)
                val index = tracePoints.indexOf(tracePoint)
                tracePoints[index] = newTracePoint
                val textAttributes = TextAttributes()
                textAttributes.backgroundColor = Color.YELLOW
                val highlighter = editor.markupModel.addLineHighlighter(newLineNumber - 1, HighlighterLayer.WARNING, textAttributes)
                highlighters[id] = highlighter
                if (!monitoredDocuments.containsKey(newFile)) {
                    val doc = FileDocumentManager.getInstance().getDocument(newFile)
                    if (doc != null) {
                        setupDocumentListener(newFile, doc)
                    }
                }
                println("Updated trace point ${tracePoint.id} to $name in ${newFile.name} at line $newLineNumber with content '$lineContent'")
            } else {
                thisLogger().warn("Trace point with ID $id not found for updating")
            }
        }
        notifyListeners()
    }

    fun deleteTracePoints(tracePointIds: List<String>) {
        tracePointIds.forEach { id ->
            val tracePoint = tracePoints.find { it.id == id }
            if (tracePoint != null) {
                val psiFile = PsiManager.getInstance(project).findFile(tracePoint.file)
                if (psiFile != null) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    if (document != null) {
                        FileEditorManager.getInstance(project).openTextEditor(
                            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, tracePoint.file), false
                        )?.let { editor ->
                            highlighters[id]?.let { highlighter ->
                                editor.markupModel.removeHighlighter(highlighter)
                            }
                        }
                    }
                }
                highlighters.remove(id)
                println("Deleted trace point: ${tracePoint.name} in ${tracePoint.fileName}")
            }
        }
        tracePoints.removeIf { tracePointIds.contains(it.id) || tracePointIds.contains(it.parentId) }
        selectedTracePoints.removeAll(tracePointIds)
        expandedTracePointIds.removeAll(tracePointIds)
        notifyListeners()
    }

    fun reorderTracePoints(orderedIds: List<String>) {
        // Create a map of trace points by ID for quick lookup
        val tracePointMap = tracePoints.associateBy { it.id }.toMutableMap()
        val newTracePoints = mutableListOf<TracePoint>()

        // Rebuild tracePoints list based on orderedIds, preserving parentId
        orderedIds.forEach { id ->
            tracePointMap[id]?.let { tracePoint ->
                newTracePoints.add(tracePoint)
                tracePointMap.remove(id) // Remove processed trace points
            }
        }

        // Add any remaining trace points (in case orderedIds is incomplete)
        newTracePoints.addAll(tracePointMap.values)

        if (newTracePoints.size == tracePoints.size) {
            tracePoints.clear()
            tracePoints.addAll(newTracePoints)
            println("Reordered trace points: ${orderedIds.joinToString()}")
            notifyListeners()
        } else {
            thisLogger().warn("Failed to reorder trace points: invalid IDs provided")
        }
    }

    fun getTracePoints(): List<TracePoint> = tracePoints.toList()

    fun getExpandedTracePointIds(): List<String> = expandedTracePointIds.toList()

    fun setExpandedTracePointIds(ids: List<String>) {
        expandedTracePointIds.clear()
        expandedTracePointIds.addAll(ids)
        notifyListeners()
    }

    fun addTracePointListener(listener: (List<TracePoint>, List<String>) -> Unit) {
        listeners.add(listener)
        listener(tracePoints, expandedTracePointIds.toList())
    }

    fun selectTracePoint(tracePointId: String, isSelected: Boolean) {
        if (isSelected) {
            selectedTracePoints.add(tracePointId)
        } else {
            selectedTracePoints.remove(tracePointId)
        }
        notifyListeners()
    }

    fun selectTracePoints(tracePointIds: List<String>) {
        selectedTracePoints.clear()
        selectedTracePoints.addAll(tracePointIds)
        notifyListeners()
    }

    fun toggleTracePointSelection(tracePointId: String) {
        if (selectedTracePoints.contains(tracePointId)) {
            selectedTracePoints.remove(tracePointId)
        } else {
            selectedTracePoints.add(tracePointId)
        }
        notifyListeners()
    }

    fun isTracePointSelected(tracePointId: String): Boolean = selectedTracePoints.contains(tracePointId)

    fun clearSelectedTracePoints() {
        selectedTracePoints.clear()
        notifyListeners()
    }

    private fun notifyListeners() {
        listeners.forEach { it(tracePoints, expandedTracePointIds.toList()) }
    }

    override fun getState(): State {
        val state = State()
        tracePoints.forEach { tracePoint ->
            state.tracePoints.add(
                TracePointData(
                    id = tracePoint.id,
                    name = tracePoint.name,
                    filePath = tracePoint.file.path,
                    lineNumber = tracePoint.lineNumber,
                    lineContent = tracePoint.lineContent,
                    parentId = tracePoint.parentId
                )
            )
        }
        state.expandedTracePointIds.addAll(expandedTracePointIds)
        println("Saving state with ${state.tracePoints.size} trace points and ${state.expandedTracePointIds.size} expanded IDs")
        return state
    }

    override fun loadState(state: State) {
        tracePoints.clear()
        highlighters.clear()
        selectedTracePoints.clear()
        expandedTracePointIds.clear()
        monitoredDocuments.clear()

        state.tracePoints.forEach { data ->
            val file = VirtualFileManager.getInstance().findFileByUrl("file://${data.filePath}")
            if (file != null && file.isValid) {
                val psiFile = PsiManager.getInstance(project).findFile(file)
                if (psiFile != null) {
                    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                    if (document != null) {
                        var lineNumber = data.lineNumber
                        var isValid = true
                        if (lineNumber <= document.lineCount) {
                            val lineStartOffset = document.getLineStartOffset(lineNumber - 1)
                            val lineEndOffset = document.getLineEndOffset(lineNumber - 1)
                            val currentLineContent = document.getText(com.intellij.openapi.util.TextRange(lineStartOffset, lineEndOffset)).trimEnd()
                            if (currentLineContent != data.lineContent) {
                                val matchingLines = mutableListOf<Int>()
                                for (line in 0 until document.lineCount) {
                                    val startOffset = document.getLineStartOffset(line)
                                    val endOffset = document.getLineEndOffset(line)
                                    val lineText = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset)).trimEnd()
                                    if (lineText == data.lineContent) {
                                        matchingLines.add(line + 1)
                                    }
                                }
                                when (matchingLines.size) {
                                    1 -> {
                                        lineNumber = matchingLines[0]
                                        println("Updated line number for trace point ${data.name} to $lineNumber in ${file.name}")
                                    }
                                    else -> {
                                        isValid = false
                                        thisLogger().warn("Trace point ${data.name} in ${file.name} is invalid: ${matchingLines.size} matches found for line content '${data.lineContent}'")
                                    }
                                }
                            }
                        } else {
                            isValid = false
                            thisLogger().warn("Trace point ${data.name} in ${file.name} is invalid: line number $lineNumber exceeds document line count ${document.lineCount}")
                        }
                        val tracePoint = TracePoint(data.id, data.name, file, lineNumber, project, data.lineContent, isValid, data.parentId)
                        tracePoints.add(tracePoint)
                        if (isValid) {
                            ApplicationManager.getApplication().invokeLater {
                                val psiFileCheck = PsiManager.getInstance(project).findFile(file)
                                if (psiFileCheck != null) {
                                    val docCheck = PsiDocumentManager.getInstance(project).getDocument(psiFileCheck)
                                    if (docCheck != null && lineNumber <= docCheck.lineCount) {
                                        FileEditorManager.getInstance(project).openTextEditor(
                                            com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                                        )?.let { editor ->
                                            val textAttributes = TextAttributes()
                                            textAttributes.backgroundColor = Color.YELLOW
                                            val highlighter = editor.markupModel.addLineHighlighter(
                                                lineNumber - 1,
                                                HighlighterLayer.WARNING,
                                                textAttributes
                                            )
                                            highlighters[data.id] = highlighter
                                        }
                                    }
                                }
                            }
                        }
                        if (!monitoredDocuments.containsKey(file)) {
                            setupDocumentListener(file, document)
                        }
                    } else {
                        thisLogger().warn("PsiFile not found for: ${data.filePath}")
                    }
                } else {
                    thisLogger().warn("PsiFile not found for: ${data.filePath}")
                }
            } else {
                thisLogger().warn("Failed to load trace point: ${data.name}, file not found: ${data.filePath}")
            }
        }
        expandedTracePointIds.addAll(state.expandedTracePointIds)
        println("Loaded state with ${tracePoints.size} trace points and ${expandedTracePointIds.size} expanded IDs")
        notifyListeners()
    }
}