/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
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
        val service = project.service<TracePointService>()
        val tracePointName = resolveNewTracePointName(
            project,
            service,
            "Enter name for the $kindLabel trace point:",
            "Create Root Trace Point",
            file.name
        ) ?: return

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
