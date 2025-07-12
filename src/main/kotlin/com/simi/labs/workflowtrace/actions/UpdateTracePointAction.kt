package com.simi.labs.workflowtrace.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile

class UpdateTracePointAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1

        // Get selected trace points from the service
        val selectedTracePoints = service.getTracePoints().filter { service.isTracePointSelected(it.id) }
        if (selectedTracePoints.isEmpty()) {
            return // No selected trace points to update
        }

        // Update the fileName and lineNumber of selected trace points
        val updatedTracePoints = service.getTracePoints().map { tracePoint ->
            if (selectedTracePoints.any { it.id == tracePoint.id }) {
                tracePoint.copy(
                    fileName = file.path,
                    lineNumber = lineNumber
                )
            } else {
                tracePoint
            }
        }

        // Update the service with the modified trace points
        service.updateTracePoints(updatedTracePoints)
    }

    override fun update(e: AnActionEvent) {
        // Enable the action only if a project, editor, and file are available, and there are selected trace points
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val hasSelectedTracePoints = project?.service<TracePointService>()?.getTracePoints()?.any { project.service<TracePointService>().isTracePointSelected(it.id) } ?: false
        e.presentation.isEnabled = project != null && editor != null && file != null && hasSelectedTracePoints
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}