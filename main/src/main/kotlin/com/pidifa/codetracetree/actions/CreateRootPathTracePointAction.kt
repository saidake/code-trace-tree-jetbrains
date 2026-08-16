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
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.util.TracePathEligibility

/** Creates a root FILE or DIRECTORY trace point from the Project View selection. */
class CreateRootPathTracePointAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!TracePathEligibility.isEligible(project, file)) return

        val kindLabel = if (file.isDirectory) "directory" else "file"
        val service = project.service<TracePointService>()
        val tracePointName = resolveNewTracePointName(
            project,
            service,
            "Enter name for the $kindLabel trace point:",
            "Create Root Trace Point",
            file.name
        ) ?: return

        val id = service.addPathTracePoint(tracePointName, file, parentId = null)
        service.markPeerProfileRefresh()
        service.notifyListeners()
        if (id != null) {
            service.revealTracePointsInTree(setOf(id), focusTree = false)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = TracePathEligibility.isEligible(project, file)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
