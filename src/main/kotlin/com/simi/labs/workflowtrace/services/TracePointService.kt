package com.simi.labs.workflowtrace.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFileManagerListener
import java.util.*

@Service(Service.Level.PROJECT)
class TracePointService(private val project: Project) {
    data class TracePoint(
        val id: String,
        val name: String,
        val fileName: String,
        val lineNumber: Int,
        val parentId: String? = null,
        val project: Project,
        val lineContent: String? = null,
        val isValid: Boolean = true
    ) {
        fun navigateTo() {
            ApplicationManager.getApplication().runReadAction {
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/$fileName")
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

    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()
    private val tracePoints = mutableListOf<TracePoint>()
    private val selectedTracePointIds = mutableSetOf<String>()
    private val expandedTracePointIds = mutableSetOf<String>()
    private val monitoredDocuments = mutableMapOf<VirtualFile, DocumentListener>()

    init {
        // Listen for file openings to attach DocumentListener
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    ApplicationManager.getApplication().runReadAction {
                        val filePath = file.path.removePrefix(project.basePath + "/")
                        if (tracePoints.any { it.fileName == filePath }) {
                            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@runReadAction
                            val listener = object : DocumentListener {
                                override fun documentChanged(event: DocumentEvent) {
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

                                        val updatedTracePoints = tracePoints.map { tracePoint ->
                                            if (tracePoint.fileName != docFilePath) return@map tracePoint
                                            when {
                                                // Update line content if changed at trace point's line
                                                tracePoint.lineNumber == changedLine -> {
                                                    val newContent = if (changedLine <= newLines.size) newLines[changedLine - 1].trim() else null
                                                    tracePoint.copy(lineContent = newContent, isValid = true)
                                                }
                                                // Adjust line number if change is above trace point
                                                tracePoint.lineNumber > changedLine && lineOffset != 0 -> {
                                                    val newLineNumber = (tracePoint.lineNumber + lineOffset).coerceAtLeast(1)
                                                    tracePoint.copy(lineNumber = newLineNumber)
                                                }
                                                else -> tracePoint
                                            }
                                        }
                                        tracePoints.clear()
                                        tracePoints.addAll(updatedTracePoints)
                                        notifyListeners()
                                    }
                                }
                            }
                            document.addDocumentListener(listener)
                            monitoredDocuments[file] = listener
                        }
                    }
                }

                override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
                    ApplicationManager.getApplication().runReadAction {
                        monitoredDocuments.remove(file)?.let { listener ->
                            FileDocumentManager.getInstance().getDocument(file)?.removeDocumentListener(listener)
                        }
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
            override fun afterRefreshFinish(isAsync: Boolean) {
                ApplicationManager.getApplication().runReadAction {
                    validateTracePointsOnLoad()
                }
            }
        }, project)
    }

    private fun validateTracePointsOnLoad() {
        ApplicationManager.getApplication().runReadAction {
            val updatedTracePoints = tracePoints.map { tracePoint ->
                val file = VirtualFileManager.getInstance().findFileByUrl("file:///${project.basePath}/${tracePoint.fileName}")
                if (file == null || tracePoint.lineContent == null) {
                    return@map tracePoint.copy(isValid = false)
                }
                val document = FileDocumentManager.getInstance().getDocument(file) ?: return@map tracePoint.copy(isValid = false)
                val lines = document.text.split("\n")
                val matchingLines = lines.mapIndexed { index, line ->
                    if (line.trim() == tracePoint.lineContent.trim()) index + 1 else null
                }.filterNotNull()
                when {
                    matchingLines.size == 1 -> {
                        val newLineNumber = matchingLines[0]
                        if (newLineNumber != tracePoint.lineNumber) {
                            tracePoint.copy(lineNumber = newLineNumber, isValid = true)
                        } else {
                            tracePoint
                        }
                    }
                    else -> tracePoint.copy(isValid = false)
                }
            }
            tracePoints.clear()
            tracePoints.addAll(updatedTracePoints)
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
            val tracePoint = TracePoint(
                id = UUID.randomUUID().toString(),
                name = name,
                fileName = file.path.removePrefix(project.basePath + "/"),
                lineNumber = lineNumber,
                parentId = parentId,
                project = project,
                lineContent = lineContent,
                isValid = true
            )
            tracePoints.add(tracePoint)
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
            tracePoints.removeAll { it.id in ids }
            selectedTracePointIds.removeAll(ids)
            expandedTracePointIds.removeAll(ids)
            notifyListeners()
        }
    }

    fun updateTracePoints(newTracePoints: List<TracePoint>) {
        ApplicationManager.getApplication().runReadAction {
            tracePoints.clear()
            tracePoints.addAll(newTracePoints)
            notifyListeners()
        }
    }

    fun getTracePoints(): List<TracePoint> {
        return ApplicationManager.getApplication().runReadAction<List<TracePoint>> {
            tracePoints.toList()
        }
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
        return ApplicationManager.getApplication().runReadAction<Boolean> {
            selectedTracePointIds.contains(id)
        }
    }
    fun setExpandedTracePointIds(ids: List<String>) {
        ApplicationManager.getApplication().runReadAction {
            expandedTracePointIds.clear()
            expandedTracePointIds.addAll(ids)
            notifyListeners()
        }
    }

    fun getExpandedTracePointIds(): List<String> {
        return ApplicationManager.getApplication().runReadAction<List<String>> {
            expandedTracePointIds.toList()
        }
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
}