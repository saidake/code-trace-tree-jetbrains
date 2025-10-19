package com.simi.labs.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.codetracetree.services.TracePointService
import com.simi.labs.codetracetree.toolWindow.MyToolWindowFactory
import com.intellij.icons.AllIcons
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ExpandSelectedTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(null, "Expand Selected", AllIcons.Actions.Expandall) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths?.toList() ?: return
        val expandedIds = service.getExpandedTracePointIds().toMutableList()

        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            val tracePoint = node.userObject as? TracePointService.TracePoint ?: return@forEach
            tree.expandPath(path)
            if (!expandedIds.contains(tracePoint.id)) {
                expandedIds.add(tracePoint.id)
            }
        }
        service.setExpandedTracePointIds(expandedIds)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths
        val isEnabled = project != null && !selectedPaths.isNullOrEmpty() &&
                selectedPaths.all { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint }
        e.presentation.isEnabled = isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}