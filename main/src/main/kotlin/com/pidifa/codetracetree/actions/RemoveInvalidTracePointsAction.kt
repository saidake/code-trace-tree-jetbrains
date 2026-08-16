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
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService

/**
 * Remove invalid LINE / FILE / DIRECTORY tips from the active profile.
 * Valid children of an invalid parent are reparented in place.
 */
class RemoveInvalidTracePointsAction : AnAction(
    null,
    "Remove invalid traces from the current profile",
    AllIcons.Actions.GC
), DumbAware {

    init {
        templatePresentation.text = "Remove Invalid Trace Points"
        templatePresentation.description = "Remove invalid traces from the current profile"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val count = service.countInvalidTracePoints()
        if (count == 0) {
            Messages.showInfoMessage(
                project,
                "No invalid trace points in the current profile.",
                "Remove Invalid Trace Points"
            )
            return
        }
        val confirm = Messages.showYesNoDialog(
            project,
            "Remove $count invalid trace point(s) from profile \"${service.getActiveProfileName()}\"? " +
                "Valid children stay and are reparented.",
            "Remove Invalid Trace Points",
            Messages.getWarningIcon()
        )
        if (confirm != Messages.YES) return
        val removed = service.removeInvalidTracePoints()
        Messages.showInfoMessage(
            project,
            if (removed == 0) "No invalid trace points to remove."
            else "Removed $removed invalid trace point(s).",
            "Remove Invalid Trace Points"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
        e.presentation.text = "Remove Invalid Trace Points"
        e.presentation.description = "Remove invalid traces from the current profile"
        e.presentation.icon = AllIcons.Actions.GC
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
