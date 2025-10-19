package com.simi.labs.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.codetracetree.services.TracePointService
import com.simi.labs.codetracetree.toolWindow.MyToolWindowFactory
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class MoveUpTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(
    null,
    "Move Up",
    AllIcons.Actions.MoveUp
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

        val allTracePoints = tracePoints.toMutableList()

        // Group trace points by their parent node
        val nodesGroupedByParent = nodesToMove.groupBy { (node, _) ->
            (node.parent as? DefaultMutableTreeNode)?.let {
                (it.userObject as? TracePointService.TracePoint)?.id ?: ""
            } ?: ""
        }
        val globalIndexMap = allTracePoints.withIndex().associate { it.value.id to it.index }.toMutableMap()
        nodesGroupedByParent.forEach { (_, nodes) ->
            val parentTracePointId = nodes.first().first.parent.let {
                (it as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePoint
            }?.id

            val siblingTracePoints = allTracePoints.filter {
                it.parentId == parentTracePointId
            }.toMutableList()

            // Move up points starting from the first selected point
            nodes.forEach { (node, id) ->
                val currentIndex = siblingTracePoints.indexOfFirst { it.id == id }
                if (currentIndex>0) {
                    val previousSibling = siblingTracePoints[currentIndex - 1]
                    if (selectedIds.contains(previousSibling.id)) return@forEach
                    val currentGlobalIndex = globalIndexMap[id]!!
                    val previousGlobalIndex = globalIndexMap[previousSibling.id]!!
                    allTracePoints[currentGlobalIndex] = allTracePoints[previousGlobalIndex].also {
                        allTracePoints[previousGlobalIndex] = allTracePoints[currentGlobalIndex]
                    }
                    siblingTracePoints[currentIndex-1] = siblingTracePoints[currentIndex].also {
                        siblingTracePoints[currentIndex] = siblingTracePoints[currentIndex-1]
                    }
                    globalIndexMap[id!!] = previousGlobalIndex
                    globalIndexMap[previousSibling.id] = currentGlobalIndex
                }
            }
        }

        service.updateTracePoints(allTracePoints)
        // Restore selection after moving
        service.selectTracePoints(selectedIds.toList())
    }

    override fun update(e: AnActionEvent) {
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths ?: run {
            e.presentation.isEnabled = false
            return
        }
        var canMoveUp = false
        for (path in selectedPaths) {
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val parent = node.parent as? DefaultMutableTreeNode ?: continue
            if (parent.getIndex(node) > 0) {
                canMoveUp = true
                break
            }
        }
        e.presentation.isEnabled = canMoveUp
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}