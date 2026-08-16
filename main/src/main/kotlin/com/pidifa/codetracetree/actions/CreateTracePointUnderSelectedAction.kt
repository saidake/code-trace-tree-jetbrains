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
import com.pidifa.codetracetree.util.TracePathEligibility

class CreateTracePointUnderSelectedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!TracePathEligibility.isEligible(project, file)) return
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
        val document = editor.document
        val startOffset = document.getLineStartOffset(lineNumber - 1)
        val endOffset = document.getLineEndOffset(lineNumber - 1)
        if (document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset)).trim().isEmpty()) {
            Messages.showWarningDialog(
                project,
                "Cannot create a line trace point on an empty line.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        // ---- selected trace points --------------------------------------------
        val selectedIds = service.getSelectedTracePointIds()

        if (selectedIds.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No trace points are selected in the Code Trace Tree tool window.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        val tracePointName = resolveNewTracePointName(
            project,
            service,
            "Enter name for the trace point:",
            "Create Trace Point (Under Selected)"
        ) ?: return
        service.setExpandedTracePointIds(service.getExpandedTracePointIds() + selectedIds)
        val createdIds = selectedIds.mapNotNull { parentId ->
            service.addTracePoint(tracePointName, file, lineNumber, parentId = parentId)
        }.toSet()
        service.attachDocumentListener(file)
        service.highlightTracePointsInFile(file)
        service.markPeerProfileRefresh()
        service.notifyListeners()
        if (createdIds.isNotEmpty()) {
            service.revealTracePointsInTree(createdIds, focusTree = false)
        }
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val eligible = editor != null && TracePathEligibility.isEligible(project, file)
        val service = project?.service<TracePointService>()
        val hasSelection = service?.getSelectedTracePointIds()?.isNotEmpty() ?: false
        e.presentation.isVisible = eligible
        e.presentation.isEnabled = eligible && hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
