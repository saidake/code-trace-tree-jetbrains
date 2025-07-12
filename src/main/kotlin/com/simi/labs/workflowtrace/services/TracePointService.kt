package com.simi.labs.workflowtrace.services

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import java.util.UUID

@Service(Service.Level.PROJECT)
@State(
    name = "com.simi.labs.workflowtrace.services.WorkflowTraceStorage",
    storages = [Storage("workflowTrace.xml")]
)
class TracePointService(project: Project) : PersistentStateComponent<TracePointService.State> {
    private var state = State()
    private val listeners = mutableListOf<(List<TracePoint>, List<String>) -> Unit>()

    @Tag("TracePoint")
    data class TracePoint(
        @Attribute("id") val id: String = "",
        @Attribute("name") val name: String = "",
        @Attribute("fileName") val fileName: String = "",
        @Attribute("lineNumber") val lineNumber: Int = 0,
        @Attribute("parentId") val parentId: String? = null,
        @Attribute("isValid") val isValid: Boolean = true
    ) {
        fun navigateTo() {
            val project = ProjectManager.getInstance().openProjects.find { it.isInitialized && !it.isDisposed } ?: return
            val file = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(fileName) ?: return
            FileEditorManager.getInstance(project).openFile(file, true)
            val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
            val document = editor.document
            val offset = document.getLineStartOffset(lineNumber - 1)
            editor.caretModel.moveToOffset(offset)
        }
    }

    @Tag("WorkflowTraceState")
    data class State(
        @Property @Tag("tracePoints") var tracePoints: List<TracePoint> = emptyList(),
        @Property @Tag("selectedTracePointIds") var selectedTracePointIds: List<String> = emptyList(),
        @Property @Tag("expandedTracePointIds") var expandedTracePointIds: List<String> = emptyList()
    )

    override fun getState(): State = state

    override fun loadState(state: State) {
        val validTracePoints = state.tracePoints.filter { tracePoint ->
            if (tracePoint.id.isEmpty()) {
                thisLogger().warn("Invalid TracePoint: id is empty")
                false
            } else if (tracePoint.name.isEmpty()) {
                thisLogger().warn("Invalid TracePoint: name is empty for id ${tracePoint.id}")
                false
            } else if (tracePoint.fileName.isEmpty()) {
                thisLogger().warn("Invalid TracePoint: fileName is empty for id ${tracePoint.id}")
                false
            } else if (tracePoint.lineNumber < 0) {
                thisLogger().warn("Invalid TracePoint: lineNumber is negative for id ${tracePoint.id}")
                false
            } else {
                true
            }
        }
        this.state = state.copy(tracePoints = validTracePoints)
        notifyListeners()
    }

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: com.intellij.openapi.editor.Editor, parentId: String? = null) {
        val newTracePoint = TracePoint(
            id = UUID.randomUUID().toString(),
            name = name,
            fileName = file.name,
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
                tracePoint.copy(parentId = tracePointMap[id]?.parentId)
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
        if (state.selectedTracePointIds != ids) {
            state = state.copy(selectedTracePointIds = ids)
            notifyListeners()
        }
    }

    fun toggleTracePointSelection(id: String) {
        val newSelectedIds = if (state.selectedTracePointIds.contains(id)) {
            state.selectedTracePointIds - id
        } else {
            state.selectedTracePointIds + id
        }
        if (state.selectedTracePointIds != newSelectedIds) {
            state = state.copy(selectedTracePointIds = newSelectedIds)
            notifyListeners()
        }
    }

    fun setExpandedTracePointIds(expandedIds: List<String>) {
        if (state.expandedTracePointIds != expandedIds) {
            state = state.copy(expandedTracePointIds = expandedIds)
            notifyListeners()
        }
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