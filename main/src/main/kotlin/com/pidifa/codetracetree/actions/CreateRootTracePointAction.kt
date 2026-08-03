/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService

class CreateRootTracePointAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        // ---- caret validation -------------------------------------------------
        val carets = editor.caretModel.getCaretsAndSelections()
        if (carets.isEmpty() || !editor.caretModel.currentCaret.isValid) {
            Messages.showWarningDialog(
                project,
                "No valid caret position found in the editor.",
                "Create Root Trace Point"
            )
            return
        }

        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        val service = project.service<TracePointService>()

        val tracePointName = resolveNewTracePointName(
            project,
            service,
            "Enter name for the trace point:",
            "Create Root Trace Point"
        ) ?: return

        // **root** → parentId = null
        service.addTracePoint(tracePointName, file, lineNumber,  parentId = null)
        service.attachDocumentListener(file)
        service.highlightTracePointsInFile(file)
        service.notifyListeners()
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = editor != null && file != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}