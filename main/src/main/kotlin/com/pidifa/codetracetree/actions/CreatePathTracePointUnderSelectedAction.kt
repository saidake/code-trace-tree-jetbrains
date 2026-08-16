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
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.util.TracePathEligibility

/** Creates a FILE or DIRECTORY child under selected tree nodes from Project View. */
class CreatePathTracePointUnderSelectedAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (!TracePathEligibility.isEligible(project, file)) return

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
        val createdIds = selectedIds.mapNotNull { parentId ->
            service.addPathTracePoint(tracePointName, file, parentId = parentId)
        }.toSet()
        service.markPeerProfileRefresh()
        service.notifyListeners()
        if (createdIds.isNotEmpty()) {
            service.revealTracePointsInTree(createdIds, focusTree = false)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val eligible = TracePathEligibility.isEligible(project, file)
        val service = project?.service<TracePointService>()
        val hasSelection = service?.getSelectedTracePointIds()?.isNotEmpty() ?: false
        e.presentation.isVisible = eligible
        e.presentation.isEnabled = eligible && hasSelection
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
