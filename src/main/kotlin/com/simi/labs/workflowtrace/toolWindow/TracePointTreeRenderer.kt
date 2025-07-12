package com.simi.labs.workflowtrace.toolWindow

import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.JLabel
import javax.swing.tree.TreePath

class TracePointTreeRenderer(
    private val service: TracePointService,
    private val toolWindow: MyToolWindowFactory.MyToolWindow
) : JLabel(), TreeCellRenderer {
    private var tree: JTree? = null

    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        this.tree = tree // Store the JTree instance
        text = when (val userObject = (value as? DefaultMutableTreeNode)?.userObject) {
            is TracePointService.TracePoint -> {
                val prefix = if (!userObject.isValid) "[Invalid] " else ""
                "$prefix${userObject.name} (${userObject.fileName}: ${userObject.lineNumber})"
            }
            else -> userObject?.toString() ?: ""
        }
        background = if (selected) Color.LIGHT_GRAY else Color.WHITE
        foreground = if ((value as? DefaultMutableTreeNode)?.userObject is TracePointService.TracePoint &&
            service.isTracePointSelected((value.userObject as TracePointService.TracePoint).id)) {
            Color.BLUE
        } else {
            Color.BLACK
        }
        isOpaque = true
        return this
    }

    override fun getPreferredSize() = super.getPreferredSize().apply {
        height += 2 // Add 2 pixels for transparent divider
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val highlightedPath = toolWindow.getHighlightedPath()
        val isHighlightOnDivider = toolWindow.isHighlightOnDivider()
        val dropPoint = toolWindow.getDropPoint()
        if (highlightedPath != null && isHighlightOnDivider && dropPoint != null) {
            val pathBounds = tree?.getPathBounds(highlightedPath)
            if (pathBounds != null) {
                g.color = Color.BLUE
                val y = if (dropPoint.y > pathBounds.y + pathBounds.height / 2) {
                    pathBounds.y + pathBounds.height
                } else {
                    pathBounds.y
                }
                g.fillRect(0, y - 1, width, 2) // Draw 2-pixel high transparent divider highlight
            }
        }
    }
}