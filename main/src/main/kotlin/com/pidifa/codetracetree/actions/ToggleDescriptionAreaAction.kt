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