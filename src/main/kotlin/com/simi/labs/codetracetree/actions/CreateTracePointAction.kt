package com.simi.labs.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.simi.labs.codetracetree.services.TracePointService

class CreateTracePointAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // Check if there is at least one valid caret
        val carets = editor.caretModel.getCaretsAndSelections()
        if (carets.isEmpty() || !editor.caretModel.currentCaret.isValid) {
            Messages.showWarningDialog(
                project,
                "No valid caret position found in the editor.",
                "Create Trace Point"
            )
            return
        }

        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        val service = project.service<TracePointService>()

        val tracePointName = Messages.showInputDialog(
            project,
            "Enter name for the trace point:",
            "Create Workflow Trace Point",
            null
        ) ?: return

        // Always add as a root trace point (no parent)
        service.addTracePoint(tracePointName, file, lineNumber, editor, parentId = null)
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