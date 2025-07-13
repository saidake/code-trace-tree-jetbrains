package com.simi.labs.workflowtrace.toolWindow

import com.simi.labs.workflowtrace.services.TracePointService
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.Font
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer
import javax.swing.JLabel

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
        val userObject = (value as? DefaultMutableTreeNode)?.userObject
        text = when (userObject) {
            is TracePointService.TracePoint -> {
                val prefix = if (!userObject.isValid) "[Invalid] " else ""
                "$prefix${userObject.name} (${userObject.fileName}: ${userObject.lineNumber})"
            }
            else -> userObject?.toString() ?: ""
        }
        font = if (userObject is TracePointService.TracePoint && !userObject.isValid) {
            font.deriveFont(Font.PLAIN or Font.ITALIC) // Strikethrough not directly supported; use italic as fallback
        } else {
            font.deriveFont(Font.PLAIN)
        }
        background = if (selected) JBColor.LIGHT_GRAY else JBColor.WHITE
        foreground = if (userObject is TracePointService.TracePoint &&
            service.isTracePointSelected(userObject.id)) {
            UIUtil.getTreeSelectionForeground()
        } else {
            UIUtil.getTreeForeground()
        }
        isOpaque = true
        return this
    }
}