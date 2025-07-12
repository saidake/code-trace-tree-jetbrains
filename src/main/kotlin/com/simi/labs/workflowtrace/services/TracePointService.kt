package com.simi.labs.workflowtrace.services

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(
    name = "com.simi.labs.workflowtrace.services.WorkflowTraceStorage",
    storages = [Storage("workflowTrace.xml")]
)
class TracePointService(project: Project) : PersistentStateComponent<TracePointService.State> {
    private var state = State()
    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()

    data class TracePoint(
        val id: String,
        val name: String,
        val fileName: String,
        val lineNumber: Int,
        val parentId: String? = null,
        val isValid: Boolean = true
    ) {
        fun navigateTo() {
            val project = ProjectManager.getInstance().openProjects.find { it.isInitialized && !it.isDisposed } ?: return
            val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(fileName) ?: return
            FileEditorManager.getInstance(project).openFile(file, true)
            val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
            val document = PsiDocumentManager.getInstance(project).getDocument(psiFile) ?: return
            val offset = document.getLineStartOffset(lineNumber - 1)
            FileEditorManager.getInstance(project).selectedTextEditor?.caretModel?.moveToOffset(offset)
        }
    }

    data class State(
        var tracePoints: List<TracePoint> = emptyList(),
        var selectedTracePointIds: List<String> = emptyList(),
        var expandedTracePointIds: List<String> = emptyList()
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
        notifyListeners()
    }

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: com.intellij.openapi.editor.Editor, parentId: String? = null) {
        val newTracePoint = TracePoint(
            id = UUID.randomUUID().toString(),
            name = name,
            fileName = file.path,
            lineNumber = lineNumber,
            parentId = parentId
        )
        state = state.copy(tracePoints = state.tracePoints + newTracePoint)
        notifyListeners()
    }

    fun renameTracePoint(id: String, newName: String) {
        state = state.copy(
            tracePoints = state.tracePoints.map { tracePoint ->
                if (tracePoint.id == id) tracePoint.copy(name = newName) else tracePoint
            }
        )
        notifyListeners()
    }

    fun deleteTracePoints(ids: List<String>) {
        state = state.copy(
            tracePoints = state.tracePoints.filter { !ids.contains(it.id) },
            selectedTracePointIds = state.selectedTracePointIds.filter { !ids.contains(it) },
            expandedTracePointIds = state.expandedTracePointIds.filter { !ids.contains(it) }
        )
        notifyListeners()
    }

    fun reorderTracePoints(orderedIds: List<String>) {
        val tracePointMap = state.tracePoints.associateBy { it.id }
        val orderedTracePoints = orderedIds.mapNotNull { id ->
            tracePointMap[id]?.let { tracePoint ->
                tracePoint.copy(parentId = tracePointMap[id]?.parentId) // Preserve parentId
            }
        }
        state = state.copy(tracePoints = orderedTracePoints)
        notifyListeners()
    }

    fun updateTracePoints(updatedTracePoints: List<TracePoint>) {
        state = state.copy(tracePoints = updatedTracePoints)
        notifyListeners()
    }

    fun selectTracePoints(ids: List<String>) {
        state = state.copy(selectedTracePointIds = ids)
        notifyListeners()
    }

    fun toggleTracePointSelection(id: String) {
        val newSelectedIds = if (state.selectedTracePointIds.contains(id)) {
            state.selectedTracePointIds - id
        } else {
            state.selectedTracePointIds + id
        }
        state = state.copy(selectedTracePointIds = newSelectedIds)
        notifyListeners()
    }

    fun setExpandedTracePointIds(expandedIds: List<String>) {
        state = state.copy(expandedTracePointIds = expandedIds)
        notifyListeners()
    }

    fun getTracePoints(): List<TracePoint> = state.tracePoints

    fun isTracePointSelected(id: String): Boolean = state.selectedTracePointIds.contains(id)

    fun getExpandedTracePointIds(): List<String> = state.expandedTracePointIds

    fun addTracePointListener(listener: (List<TracePoint>, List<String>) -> Unit) {
        listeners.add(listener)
        listener(state.tracePoints, state.expandedTracePointIds)
    }

    private fun notifyListeners() {
        listeners.forEach { it(state.tracePoints, state.expandedTracePointIds) }
    }
}