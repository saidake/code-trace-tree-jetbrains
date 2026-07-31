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

/** Creates a root FILE or DIRECTORY trace point from the Project View selection. */
class CreateRootPathTracePointAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val basePath = project.basePath ?: return
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return
        if (!VfsUtilCore.isAncestor(projectRoot, file, false)) {
            Messages.showWarningDialog(
                project,
                "Select a file or directory inside the project.",
                "Create Root Trace Point"
            )
            return
        }

        val kindLabel = if (file.isDirectory) "directory" else "file"
        val tracePointName = Messages.showInputDialog(
            project,
            "Enter name for the $kindLabel trace point:",
            "Create Root Trace Point",
            null,
            file.name,
            null
        ) ?: return

        val service = project.service<TracePointService>()
        service.addPathTracePoint(tracePointName, file, parentId = null)
        service.notifyListeners()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
