/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pidifa.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService

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
        selectedIds.forEach { parentId ->
            service.addTracePoint(tracePointName, file, lineNumber,  parentId = parentId)
        }
        service.attachDocumentListener(file)
        service.highlightTracePointsInFile(file)
        service.notifyListeners()
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val project = e.project
        val service = project?.service<TracePointService>()
        val hasSelection = service?.getSelectedTracePointIds()?.isNotEmpty() ?: false
        e.presentation.isEnabled = editor != null && file != null && hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
