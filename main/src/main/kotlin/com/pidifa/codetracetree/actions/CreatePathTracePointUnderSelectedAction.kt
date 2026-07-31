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
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.pidifa.codetracetree.services.TracePointService

/** Creates a FILE or DIRECTORY child under selected tree nodes from Project View. */
class CreatePathTracePointUnderSelectedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        if (!VfsUtilCore.isAncestor(projectRoot, file, false)) {
            Messages.showWarningDialog(
                project,
                "Select a file or directory inside the project.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        val service = project.service<TracePointService>()
        val selectedIds = service.getSelectedTracePointIds()
        if (selectedIds.isEmpty()) {
            Messages.showInfoMessage(
                project,
                "No trace points are selected in the Code Trace Tree tool window.",
                "Create Trace Point (Under Selected)"
            )
            return
        }

        val kindLabel = if (file.isDirectory) "directory" else "file"
        val tracePointName = resolveNewTracePointName(
            project,
            service,
            "Enter name for the $kindLabel trace point:",
            "Create Trace Point (Under Selected)",
            file.name
        ) ?: return

        service.setExpandedTracePointIds(service.getExpandedTracePointIds() + selectedIds)
        selectedIds.forEach { parentId ->
            service.addPathTracePoint(tracePointName, file, parentId = parentId)
        }
        service.notifyListeners()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val service = project?.service<TracePointService>()
        val hasSelection = service?.getSelectedTracePointIds()?.isNotEmpty() ?: false
        e.presentation.isEnabledAndVisible = project != null && file != null && hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
