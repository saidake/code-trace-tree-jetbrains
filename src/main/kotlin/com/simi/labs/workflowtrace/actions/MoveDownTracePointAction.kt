package com.simi.labs.workflowtrace.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.simi.labs.workflowtrace.toolWindow.MyToolWindowFactory
import com.intellij.icons.AllIcons
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class MoveDownTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(null, "Move Down", AllIcons.Actions.MoveDown) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val selectedPath = tree.selectionPath ?: return
        val selectedNode = selectedPath.lastPathComponent as? DefaultMutableTreeNode ?: return
        val tracePoint = selectedNode.userObject as? TracePointService.TracePoint ?: return
        val parentNode = selectedNode.parent as? DefaultMutableTreeNode ?: return
        val index = parentNode.getIndex(selectedNode)
        if (index >= parentNode.childCount - 1) return // Already at the bottom

        // Remove and reinsert node at new position
        parentNode.remove(selectedNode)
        parentNode.insert(selectedNode, index + 1)

        // Update TracePointService with new order
        val updatedTracePoints = service.getTracePoints().toMutableList()
        val tracePointIndex = updatedTracePoints.indexOfFirst { it.id == tracePoint.id }
        if (tracePointIndex >= 0 && tracePointIndex < updatedTracePoints.size - 1) {
            val sibling = updatedTracePoints.filter { it.parentId == tracePoint.parentId }
            val siblingIndex = sibling.indexOfFirst { it.id == tracePoint.id }
            if (siblingIndex < sibling.size - 1) {
                val swapIndex = updatedTracePoints.indexOf(sibling[siblingIndex + 1])
                updatedTracePoints[tracePointIndex] = updatedTracePoints[swapIndex].copy()
                updatedTracePoints[swapIndex] = tracePoint.copy()
            }
        }
        service.updateTracePoints(updatedTracePoints)

        // Update selection
        tree.selectionPath = TreePath(selectedNode.path)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val selectedPath = tree.selectionPath
        val isEnabled = project != null &&
                selectedPath != null &&
                tree.selectionCount == 1 &&
                (selectedPath.lastPathComponent as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint &&
                (selectedPath.lastPathComponent as DefaultMutableTreeNode).let { node ->
                    val parent = node.parent as? DefaultMutableTreeNode
                    parent != null && parent.getIndex(node) < parent.childCount - 1
                }
        e.presentation.isEnabled = isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}