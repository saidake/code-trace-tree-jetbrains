package com.simi.labs.workflowtrace.toolWindow

import com.intellij.ui.components.JBLabel
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class TracePointTreeRenderer(private val service: TracePointService) : TreeCellRenderer {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        val node = value as? DefaultMutableTreeNode
        val tracePoint = node?.userObject as? TracePointService.TracePoint
        val label = if (tracePoint != null) {
            JBLabel("${tracePoint.name} (${tracePoint.fileName}:${tracePoint.lineNumber})")
        } else {
            JBLabel("Root")
        }
        if (tracePoint != null) {
            val isPersistentlySelected = service.isTracePointSelected(tracePoint.id)
            if (isPersistentlySelected) {
                label.background = Color(0, 120, 215) // Custom selected background (blue)
                label.foreground = Color.WHITE // White text for contrast
            } else {
                label.background = tree.background
                label.foreground = tree.foreground
            }
            if (!tracePoint.isValid) {
                val font = label.font
                label.font = font.deriveFont(font.style or Font.ITALIC)
                label.text = "<html><strike>${label.text}</strike></html>"
            }
        }
        label.isOpaque = true
        return label
    }
}