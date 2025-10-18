package com.simi.labs.workflowtrace.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.simi.labs.workflowtrace.toolWindow.MyToolWindowFactory
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class MoveDownTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(
    null,
    "Move Down",
    AllIcons.Actions.MoveDown
) {
    override fun actionPerformed(e: AnActionEvent) {
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths ?: return
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tracePoints = service.getTracePoints()
        val selectedIds = selectedPaths.mapNotNull { path ->
            (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
        }.map { it.id }.toSet()
        val nodesToMove = selectedPaths.mapNotNull { path ->
            (path.lastPathComponent as? DefaultMutableTreeNode)?.let { node ->
                Pair(node, (node.userObject as? TracePointService.TracePoint)?.id)
            }
        }.filter { it.second != null }

        val updatedTracePoints = tracePoints.toMutableList()
        nodesToMove.reversed().forEach { (node, id) ->
            val parent = node.parent as? DefaultMutableTreeNode ?: return@forEach
            val index = parent.getIndex(node)
            if (index < parent.childCount - 1) {
                val siblingTracePoints = updatedTracePoints.filter {
                    it.parentId == (node.userObject as TracePointService.TracePoint).parentId
                }.sortedBy { updatedTracePoints.indexOf(it) }
                val currentIndex = siblingTracePoints.indexOfFirst { it.id == id }
                if (currentIndex < siblingTracePoints.size - 1) {
                    val currentGlobalIndex = updatedTracePoints.indexOfFirst { it.id == id }
                    val nextSibling = siblingTracePoints[currentIndex + 1]
                    val nextGlobalIndex = updatedTracePoints.indexOfFirst { it.id == nextSibling.id }
                    updatedTracePoints[currentGlobalIndex] = updatedTracePoints[nextGlobalIndex].also {
                        updatedTracePoints[nextGlobalIndex] = updatedTracePoints[currentGlobalIndex]
                    }
                }
            }
        }

        service.updateTracePoints(updatedTracePoints)
        // Restore selection after moving
        service.selectTracePoints(selectedIds.toList())
    }

    override fun update(e: AnActionEvent) {
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths ?: run {
            e.presentation.isEnabled = false
            return
        }
        var canMoveDown = false
        for (path in selectedPaths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val parent = node.parent as? DefaultMutableTreeNode ?: continue
            if (parent.getIndex(node) < parent.childCount - 1) {
                canMoveDown = true
                break
            }
        }
        e.presentation.isEnabled = canMoveDown
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}