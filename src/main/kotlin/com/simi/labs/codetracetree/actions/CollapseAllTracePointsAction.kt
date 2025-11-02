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

class CollapseAllTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(null, "Collapse All", AllIcons.Actions.Collapseall) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val rootNode = tree.model.root as? DefaultMutableTreeNode ?: return
        val pathsToCollapse = mutableListOf<TreePath>()

        myToolWindow.traverseTreeNodes(rootNode) { node ->
            val tracePoint = (node as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode
            if (tracePoint != null) {
                pathsToCollapse.add(TreePath(node.path))
            }
            true
        }

        pathsToCollapse.forEach { path ->
            tree.collapsePath(path)
        }
        service.setExpandedTracePointIds(emptySet())
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val rootNode = tree.model.root as? DefaultMutableTreeNode
        var hasTracePoints = false
        if (rootNode != null) {
            myToolWindow.traverseTreeNodes(rootNode) { node ->
                if ((node as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint) {
                    hasTracePoints = true
                    false // Stop traversal as soon as a TracePoint is found
                } else {
                    true
                }
            }
        }
        e.presentation.isEnabled = project != null && hasTracePoints
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}