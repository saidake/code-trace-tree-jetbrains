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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.wm.ToolWindowManager
import com.pidifa.codetracetree.services.TracePointService

class GoToTracePointInTreeAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!isSingleLineSelection(editor)) return

        val service = project.service<TracePointService>()
        val projectPath = project.basePath ?: return
        val filePath = file.path.removePrefix("$projectPath/")
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        val matches = service.findValidTracePointsAt(filePath, lineNumber)
        if (matches.isEmpty()) return

        val ids = matches.map { it.id }.toSet()
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Code Trace Tree")
        if (toolWindow != null) {
            toolWindow.show { service.revealTracePointsInTree(ids) }
        } else {
            service.revealTracePointsInTree(ids)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val visible = project != null && editor != null && file != null &&
            isSingleLineSelection(editor) &&
            hasTracePointAtCaret(project, editor, file.path, project.basePath)
        e.presentation.isEnabledAndVisible = visible
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun isSingleLineSelection(editor: Editor): Boolean {
        val selectionModel = editor.selectionModel
        if (!selectionModel.hasSelection()) return true
        val document = editor.document
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        val endForLine = if (end > start) end - 1 else end
        return document.getLineNumber(start) == document.getLineNumber(endForLine)
    }

    private fun hasTracePointAtCaret(
        project: com.intellij.openapi.project.Project,
        editor: Editor,
        absoluteFilePath: String,
        projectBasePath: String?
    ): Boolean {
        val service = project.service<TracePointService>()
        val filePath = absoluteFilePath.removePrefix(projectBasePath?.let { "$it/" } ?: "")
        val lineNumber = editor.document.getLineNumber(editor.caretModel.offset) + 1
        return service.findValidTracePointsAt(filePath, lineNumber).isNotEmpty()
    }
}
