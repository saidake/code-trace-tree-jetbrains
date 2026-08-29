/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.domain.TreeOps
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.services.TracePointService.TracePointNode
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory
import javax.swing.tree.DefaultMutableTreeNode

class MoveUpTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(
    null,
    "Move the selected trace point(s) one position up among siblings in the tree",
    AllIcons.Actions.MoveUp
) {
    init {
        templatePresentation.text = "Move Up"
        templatePresentation.description =
            "Move the selected trace point(s) one position up among siblings in the tree"
    }
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

        for ((parentId, _) in groupedByParent) {
            val parentNode = parentId?.let { service.getTracePointNodeById(parentId) }
            val siblings = parentNode?.children ?: tracePointNodes
            TreeOps.moveSiblingsUp(siblings, selectedIds) { it.id }
        }

//        service.updateNodeMap(allTracePoints)
//        service.refreshDocumentListener(allTracePoints)
        service.selectTracePoints(selectedIds)
        service.markPeerProfileRefresh()
        service.notifyListeners(true)
        // Restore selection after moving
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