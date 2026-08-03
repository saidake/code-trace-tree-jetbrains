/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory
import com.intellij.icons.AllIcons
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class ExpandSelectedTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(null, "Expand Selected", AllIcons.Actions.Expandall) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths?.toList() ?: return
        val expandedIds = service.getExpandedTracePointIds().toMutableSet()

        myToolWindow.beginTreeUpdate()
        selectedPaths.forEach { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@forEach
            val tracePointNode = node.userObject as? TracePointService.TracePointNode ?: return@forEach
            if (!expandedIds.contains(tracePointNode.id)) {
                expandedIds.add(tracePointNode.id)
            }
            tree.expandPath(path)
            myToolWindow.traverseTreeNodes(node){node ->
                tree.expandPath(TreePath(node.path))
                if (!expandedIds.contains(tracePointNode.id)) {
                    expandedIds.add(tracePointNode.id)
                }
                true
            }
        }
        myToolWindow.endTreeUpdate()
        service.setExpandedTracePointIds(expandedIds)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val selectedPaths = tree.selectionPaths
        val isEnabled = project != null && !selectedPaths.isNullOrEmpty() &&
                selectedPaths.all { (it.lastPathComponent as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePointNode }
        e.presentation.isEnabled = isEnabled
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
