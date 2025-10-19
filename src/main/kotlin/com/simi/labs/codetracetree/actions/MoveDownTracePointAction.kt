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

        val allTracePoints = tracePoints.toMutableList()

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

            // Move down points starting from the last selected point
            nodes.asReversed().forEach { (node, id) ->
                val currentIndex = siblingTracePoints.indexOfFirst { it.id == id }
                if (currentIndex < siblingTracePoints.size - 1) {
                    val nextSibling = siblingTracePoints[currentIndex + 1]
                    if (selectedIds.contains(nextSibling.id)) return@forEach
                    val currentGlobalIndex = globalIndexMap[id]!!
                    val nextGlobalIndex = globalIndexMap[nextSibling.id]!!
                    allTracePoints[currentGlobalIndex] = allTracePoints[nextGlobalIndex].also {
                        allTracePoints[nextGlobalIndex] = allTracePoints[currentGlobalIndex]
                    }
                    siblingTracePoints[currentIndex + 1] = siblingTracePoints[currentIndex].also {
                        siblingTracePoints[currentIndex] = siblingTracePoints[currentIndex + 1]
                    }
                    globalIndexMap[id!!] = nextGlobalIndex
                    globalIndexMap[nextSibling.id] = currentGlobalIndex
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