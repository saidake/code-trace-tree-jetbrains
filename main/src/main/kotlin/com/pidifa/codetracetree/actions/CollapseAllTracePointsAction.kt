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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.pidifa.codetracetree.services.TracePointService
import com.pidifa.codetracetree.toolWindow.MyToolWindowFactory
import com.intellij.icons.AllIcons
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class CollapseAllTracePointAction(private val myToolWindow: MyToolWindowFactory.MyToolWindow) : AnAction(null, "Collapse All", AllIcons.Actions.Collapseall) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val tree = myToolWindow.getTree()
        val rootNode = tree.model.root as? DefaultMutableTreeNode ?: return
        service.setExpandedTracePointIds(emptySet())
        myToolWindow.collapseAll()
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val tree = myToolWindow.getTree()
        val rootNode = tree.model.root as? DefaultMutableTreeNode
        var hasTracePoints = false
        if (rootNode != null) {
            myToolWindow.traverseTreeNodes(rootNode) { node ->
                if ((node as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePointNode) {
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