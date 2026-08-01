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
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.storage.ClaudeAssistTarget

class ToggleClaudeAssistAction : ToggleAction(
    null,
    "Claude Assist",
    AllIcons.Actions.IntentionBulb
) {
    init {
        templatePresentation.text = "Claude Assist"
        templatePresentation.description =
            "When enabled, Claude may auto-sync topic-related trace points each turn that touched code"
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return project.service<TracePointService>().isClaudeAssistEnabled()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        if (!state) {
            service.setClaudeAssistEnabled(false)
            return
        }

        val choice = Messages.showDialog(
            project,
            "Claude will add, update, or remove topic-related trace points and short descriptions " +
                "each turn that touched code.\n\nWhere should those traces be written?",
            "Enable Claude Assist",
            arrayOf("Current Profile", "CLAUDE Profile", "Cancel"),
            0,
            Messages.getQuestionIcon()
        )
        when (choice) {
            0 -> service.enableClaudeAssist(ClaudeAssistTarget.CURRENT)
            1 -> service.enableClaudeAssist(ClaudeAssistTarget.CLAUDE)
            else -> {
                // Stay disabled (isSelected still false).
            }
        }
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
        val project = e.project
        if (project != null && project.service<TracePointService>().isClaudeAssistEnabled()) {
            val target = project.service<TracePointService>().getClaudeAssistTarget()
            val targetLabel = when (target) {
                ClaudeAssistTarget.CURRENT -> "current profile"
                ClaudeAssistTarget.CLAUDE -> "CLAUDE profile"
            }
            e.presentation.description =
                "Claude Assist is on ($targetLabel). Click to disable auto-sync of topic-related traces."
        } else {
            e.presentation.description =
                "When enabled, Claude may auto-sync topic-related trace points each turn that touched code"
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
