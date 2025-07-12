package com.simi.labs.workflowtrace.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.simi.labs.workflowtrace.services.TracePointService

class CreateTracePointAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        val service = project.service<TracePointService>()

        val tracePointName = Messages.showInputDialog(
            project,
            "Enter name for the trace point:",
            "Create Workflow Trace Point",
            null
        ) ?: return

        // Check selected trace points to determine if the new trace point should have a parent
        val selectedTracePoints = service.getTracePoints().filter { service.isTracePointSelected(it.id) }
        val parentId = if (selectedTracePoints.size == 1) {
            selectedTracePoints.first().id
        } else {
            null
        }

        service.addTracePoint(tracePointName, file, lineNumber, editor, parentId)
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = editor != null && file != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}