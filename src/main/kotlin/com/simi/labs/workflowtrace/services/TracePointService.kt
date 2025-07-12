package com.simi.labs.workflowtrace.services

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
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
        val lineNumber: Int,
        val project: Project
    ) {
        val fileName: String get() = file.name
        fun navigateTo() {
            thisLogger().info("Navigating to trace point: $name in ${file.name} at line $lineNumber")
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
                    thisLogger().info("Navigation successful to offset $offset in ${file.name}")
                } catch (e: Exception) {
                    thisLogger().error("Error navigating to trace point: ${e.message}", e)
                }
            }
        }
    }

    data class State(
        @XCollection(elementTypes = [TracePointData::class])
        val tracePoints: MutableList<TracePointData> = mutableListOf()
    )

    data class TracePointData(
        @Attribute var id: String = "",
        @Attribute var name: String = "",
        @Attribute var filePath: String = "",
        @Attribute var lineNumber: Int = 0
    )

    private val tracePoints = mutableListOf<TracePoint>()
    private val listeners = mutableListOf<(List<TracePoint>) -> Unit>()
    private val highlighters = mutableMapOf<String, RangeHighlighter>()
    private val selectedTracePoints = mutableSetOf<String>()

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: Editor) {
        val tracePoint = TracePoint(UUID.randomUUID().toString(), name, file, lineNumber, project)
        tracePoints.add(tracePoint)

        val textAttributes = TextAttributes()
        textAttributes.backgroundColor = Color.YELLOW
        val highlighter = editor.markupModel.addLineHighlighter(lineNumber - 1, HighlighterLayer.WARNING, textAttributes)
        highlighters[tracePoint.id] = highlighter

        thisLogger().info("Added trace point: $name in ${file.name} at line $lineNumber")
        notifyListeners()
    }

    fun renameTracePoint(tracePointId: String, newName: String) {
        val tracePoint = tracePoints.find { it.id == tracePointId }
        if (tracePoint != null) {
            tracePoint.name = newName
            thisLogger().info("Renamed trace point ${tracePoint.id} to $newName")
            notifyListeners()
        } else {
            thisLogger().warn("Trace point with ID $tracePointId not found for renaming")
        }
    }

    fun updateTracePoints(tracePointIdNamePairs: List<Pair<String, String>>, newFile: VirtualFile, newLineNumber: Int, editor: Editor) {
        tracePointIdNamePairs.forEach { (id, name) ->
            val tracePoint = tracePoints.find { it.id == id }
            if (tracePoint != null) {
                // Remove old highlighter
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
                // Update trace point
                val newTracePoint = TracePoint(tracePoint.id, name, newFile, newLineNumber, project)
                val index = tracePoints.indexOf(tracePoint)
                tracePoints[index] = newTracePoint
                // Add new highlighter
                val textAttributes = TextAttributes()
                textAttributes.backgroundColor = Color.YELLOW
                val highlighter = editor.markupModel.addLineHighlighter(newLineNumber - 1, HighlighterLayer.WARNING, textAttributes)
                highlighters[id] = highlighter
                thisLogger().info("Updated trace point ${tracePoint.id} to $name in ${newFile.name} at line $newLineNumber")
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
                thisLogger().info("Deleted trace point: ${tracePoint.name} in ${tracePoint.fileName}")
            }
        }
        tracePoints.removeIf { tracePointIds.contains(it.id) }
        selectedTracePoints.removeAll(tracePointIds)
        notifyListeners()
    }

    fun reorderTracePoints(orderedIds: List<String>) {
        val newTracePoints = mutableListOf<TracePoint>()
        orderedIds.forEach { id ->
            tracePoints.find { it.id == id }?.let { newTracePoints.add(it) }
        }
        if (newTracePoints.size == tracePoints.size) {
            tracePoints.clear()
            tracePoints.addAll(newTracePoints)
            thisLogger().info("Reordered trace points: ${orderedIds.joinToString()}")
            notifyListeners()
        } else {
            thisLogger().warn("Failed to reorder trace points: invalid IDs provided")
        }
    }

    fun getTracePoints(): List<TracePoint> = tracePoints.toList()

    fun addTracePointListener(listener: (List<TracePoint>) -> Unit) {
        listeners.add(listener)
        listener(tracePoints)
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
        listeners.forEach { it(tracePoints) }
    }

    override fun getState(): State {
        val state = State()
        tracePoints.forEach { tracePoint ->
            state.tracePoints.add(
                TracePointData(
                    id = tracePoint.id,
                    name = tracePoint.name,
                    filePath = tracePoint.file.path,
                    lineNumber = tracePoint.lineNumber
                )
            )
        }
        thisLogger().info("Saving state with ${state.tracePoints.size} trace points")
        return state
    }

    override fun loadState(state: State) {
        tracePoints.clear()
        highlighters.clear()
        selectedTracePoints.clear()

        state.tracePoints.forEach { data ->
            val file = VirtualFileManager.getInstance().findFileByUrl("file://${data.filePath}")
            if (file != null && file.isValid) {
                val tracePoint = TracePoint(data.id, data.name, file, data.lineNumber, project)
                tracePoints.add(tracePoint)
                ApplicationManager.getApplication().invokeLater {
                    val psiFile = PsiManager.getInstance(project).findFile(file)
                    if (psiFile != null) {
                        val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
                        if (document != null && data.lineNumber <= document.lineCount) {
                            FileEditorManager.getInstance(project).openTextEditor(
                                com.intellij.openapi.fileEditor.OpenFileDescriptor(project, file), false
                            )?.let { editor ->
                                val textAttributes = TextAttributes()
                                textAttributes.backgroundColor = Color.YELLOW
                                val highlighter = editor.markupModel.addLineHighlighter(
                                    data.lineNumber - 1,
                                    HighlighterLayer.WARNING,
                                    textAttributes
                                )
                                highlighters[data.id] = highlighter
                            }
                        }
                    }
                }
            } else {
                thisLogger().warn("Failed to load trace point: ${data.name}, file not found: ${data.filePath}")
            }
        }
        thisLogger().info("Loaded state with ${tracePoints.size} trace points")
        notifyListeners()
    }
}