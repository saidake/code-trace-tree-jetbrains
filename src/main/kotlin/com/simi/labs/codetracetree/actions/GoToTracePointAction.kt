package com.simi.labs.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.simi.labs.codetracetree.toolWindow.MyToolWindowFactory
import com.simi.labs.codetracetree.services.TracePointService
import javax.swing.tree.DefaultMutableTreeNode

class GoToTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(
    null,
    "Go to Trace Point",
    AllIcons.Actions.Back
) {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = myToolWindow.getTree()
        val selectedPath = tree.selectionPath ?: return
        val node = selectedPath.lastPathComponent as? DefaultMutableTreeNode ?: return
        val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return
        tracePointNode.tracePoint.navigateTo(e.project ?: return)
    }

    override fun update(e: AnActionEvent) {
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths
        e.presentation.isEnabled = selectedPaths?.size == 1 && selectedPaths.firstOrNull()?.lastPathComponent is DefaultMutableTreeNode
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}