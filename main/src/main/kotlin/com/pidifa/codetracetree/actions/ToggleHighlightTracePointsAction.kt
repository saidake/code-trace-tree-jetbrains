package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService

class ToggleHighlightTracePointsAction : ToggleAction(
    null,
    "Toggle Trace Point Highlights",
    AllIcons.Actions.Show
) {
    init {
        templatePresentation.text = "Toggle Highlights"
        templatePresentation.description = "Toggle the visibility of trace point highlights in files"
    }

    override fun isSelected(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        val service = project.service<TracePointService>()
        return service.isHighlightingEnabled()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        service.setHighlightingEnabled(state)
    }

    override fun update(e: AnActionEvent) {
        super.update(e)
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}