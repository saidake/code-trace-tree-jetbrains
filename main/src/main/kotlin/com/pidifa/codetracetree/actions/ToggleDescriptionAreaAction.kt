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

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory

class ToggleDescriptionAreaAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : ToggleAction(
    null,
    "Toggle Description Area",
    AllIcons.General.Information
) {
    init {
        templatePresentation.text = "Toggle Description"
        templatePresentation.description = "Show or hide the description area for the selected trace point"
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val service = project.service<TracePointService>()
        return service.isDescriptionAreaOpened()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        myToolWindow.setDescriptionAreaVisible(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}