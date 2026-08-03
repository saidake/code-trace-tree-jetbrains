/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService

class ToggleNamePromptAction : ToggleAction(
    null,
    "Prompt for Trace Point Name",
    AllIcons.Actions.Edit
) {
    init {
        templatePresentation.text = "Prompt for Name"
        templatePresentation.description =
            "When enabled, ask for a name when creating a trace point; when disabled, create with an empty name"
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return project.service<TracePointService>().isNamePromptEnabled()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        project.service<TracePointService>().setNamePromptEnabled(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
