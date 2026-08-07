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
import com.intellij.openapi.project.DumbAware
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory

/**
 * Toggle: selected = description fills the area under the profile (tree hidden);
 * unselected = restore the trace points tree.
 */
class ToggleMaximizeDescriptionAction(
    private val myToolWindow: MyToolWindowFactory.MyToolWindow
) : ToggleAction(
    null,
    "Maximize Description",
    AllIcons.General.ExpandComponent
), DumbAware {
    init {
        templatePresentation.text = "Maximize Description"
        templatePresentation.description =
            "Toggle full-height description (hides or restores the trace points tree)"
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        return myToolWindow.isDescriptionMaximized()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        myToolWindow.setDescriptionMaximized(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        // Always keep this toggle clickable while the tool window exists.
        e.presentation.isEnabled = true
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}
