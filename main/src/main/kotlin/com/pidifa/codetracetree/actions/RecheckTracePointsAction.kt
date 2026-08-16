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
 * Reload storage from disk, then recheck LINE / FILE / DIRECTORY traces against the project.
 */
class RecheckTracePointsAction : AnAction(
    null,
    "Reload stored data and recheck line, file, and directory traces against the project",
    AllIcons.Actions.Refresh
), DumbAware {

    init {
        templatePresentation.text = "Recheck Trace Points"
        templatePresentation.description =
            "Reload stored data and recheck line, file, and directory traces against the project"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        project.service<TracePointService>().recheckAllTracePoints()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        e.presentation.text = "Recheck Trace Points"
        e.presentation.description =
            "Reload stored data and recheck line, file, and directory traces against the project"
        e.presentation.icon = AllIcons.Actions.Refresh
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
