/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.services.TracePointService.TracePointNode
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory
import javax.swing.tree.DefaultMutableTreeNode

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
        val selectedIds = selectedPaths.mapNotNull { path ->
            (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? TracePointService.TracePointNode
        }.map { it.id }.toSet()

        val tracePointNodes: MutableList<TracePointNode> = service.getTracePoints()
        val groupedByParent = selectedIds
            .mapNotNull { service.getTracePointNodeById(it) }
            .groupBy { it.parentId }

        for ((parentId, nodes) in groupedByParent) {
            val parentNode = if (parentId == null) null else service.getTracePointNodeById(parentId)
            val originalSiblings = parentNode?.children ?: tracePointNodes
            val orderedSelected = nodes.sortedBy { originalSiblings.indexOf(it) }

            for (node in orderedSelected.asReversed()) {
                val originalIndex = originalSiblings.indexOf(node)
                if (originalIndex < originalSiblings.size - 1
                    && !selectedIds.contains(originalSiblings[originalIndex + 1].id)) {
                    originalSiblings[originalIndex] = originalSiblings[originalIndex + 1].also {
                        originalSiblings[originalIndex + 1] = originalSiblings[originalIndex]
                    }
                }
            }
        }

//        service.updateNodeMap(updateTracePoints)
//        service.refreshDocumentListener(allTracePoints)
        service.selectTracePoints(selectedIds)
        service.notifyListeners(true)
        // Restore selection after moving
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