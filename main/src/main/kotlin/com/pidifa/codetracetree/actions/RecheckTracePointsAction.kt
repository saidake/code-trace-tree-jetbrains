/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.pidifa.codetracetree.services.TracePointService

/**
 * Reload storage from disk, then recheck whether LINE / FILE / DIRECTORY traces are still available.
 */
class RecheckTracePointsAction : AnAction(
    null,
    "Recheck whether line, file, and directory traces are still available",
    AllIcons.Actions.Refresh
), DumbAware {

    init {
        templatePresentation.text = "Recheck Trace Availability"
        templatePresentation.description =
            "Recheck whether line, file, and directory traces are still available"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<TracePointService>().recheckAllTracePoints()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        e.presentation.text = "Recheck Trace Availability"
        e.presentation.description =
            "Recheck whether line, file, and directory traces are still available"
        e.presentation.icon = AllIcons.Actions.Refresh
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
