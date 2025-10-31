package com.simi.labs.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.simi.labs.codetracetree.services.TracePointService

class CreateTracePointUnderSelectedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = project.service<TracePointService>()

        // ---- caret validation -------------------------------------------------
        val carets = editor.caretModel.getCaretsAndSelections()
        if (carets.isEmpty() || !editor.caretModel.currentCaret.isValid) {
            Messages.showWarningDialog(
                project,
                "No valid caret position found in the editor.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1

        // ---- selected trace points --------------------------------------------
        val selectedIds = service.getTracePoints()
            .filter { service.isTracePointSelected(it.id) }
            .map { it.id }

        if (selectedIds.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No trace points are selected in the Code Trace Tree tool window.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        val tracePointName = Messages.showInputDialog(
            project,
            "Enter name for the trace point:",
            "Create Trace Point (Under Selected)",
            null
        ) ?: return

        // ---- create one child under **each** selected parent ------------------
        selectedIds.forEach { parentId ->
            service.addTracePoint(tracePointName, file, lineNumber, editor, parentId = parentId)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val service = project?.service<TracePointService>()
        val hasSelection = service?.let {
            it.getTracePoints().any { tp -> it.isTracePointSelected(tp.id) }
        } ?: false

        e.presentation.isEnabled = editor != null && file != null && hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}