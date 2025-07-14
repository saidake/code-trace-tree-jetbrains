package com.simi.labs.workflowtrace.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.simi.labs.workflowtrace.toolWindow.MyToolWindowFactory
import javax.swing.tree.DefaultMutableTreeNode

class GoToTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(
    null,
    "Go to Trace Point",
    AllIcons.Actions.Show
) {
    init {
        templatePresentation.text = "Go to Trace Point"
        templatePresentation.description = "Navigate to the selected trace point's file and line"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val selectedPath = tree.selectionPath ?: return
        val node = selectedPath.lastPathComponent as? DefaultMutableTreeNode ?: return
        val tracePoint = node.userObject as? TracePointService.TracePoint ?: return

        tracePoint.navigateTo(project)
        service.selectTracePoints(listOf(tracePoint.id))
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val selectedPath = tree.selectionPath
        val isEnabled = project != null &&
                selectedPath != null &&
                (selectedPath.lastPathComponent as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint
        e.presentation.isEnabled = isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}