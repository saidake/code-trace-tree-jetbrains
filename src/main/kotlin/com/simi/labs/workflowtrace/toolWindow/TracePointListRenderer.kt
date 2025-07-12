package com.simi.labs.workflowtrace.toolWindow

import com.intellij.ui.components.JBLabel
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.*
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreeCellRenderer

class TracePointTreeRenderer(
    private val service: TracePointService,
    private val myToolWindow: MyToolWindowFactory.MyToolWindow
) : TreeCellRenderer {
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
        val panel = JPanel(BorderLayout())
        panel.isOpaque = true

        // Create label for trace point or root
        val label = if (tracePoint != null) {
            JBLabel("${tracePoint.name} (${tracePoint.fileName}:${tracePoint.lineNumber})")
        } else {
            JBLabel("Root")
        }

        // Styling for trace point
        if (tracePoint != null) {
            val isPersistentlySelected = service.isTracePointSelected(tracePoint.id)
            if (isPersistentlySelected) {
                label.background = Color(0, 120, 215) // Blue for selected
                label.foreground = Color.WHITE
                panel.background = Color(0, 120, 215)
            } else {
                label.background = tree.background
                label.foreground = tree.foreground
                panel.background = tree.background
            }
            if (!tracePoint.isValid) {
                val font = label.font
                label.font = font.deriveFont(font.style or Font.ITALIC)
                label.text = "<html><strike>${label.text}</strike></html>"
            }
        } else {
            label.background = tree.background
            label.foreground = tree.foreground
            panel.background = tree.background
        }
        label.isOpaque = true

        // Get highlight state from MyToolWindow
        val highlightedPath = myToolWindow.getHighlightedPath()
        val isHighlightOnDivider = myToolWindow.isHighlightOnDivider()
        val isHighlightedNode = highlightedPath?.lastPathComponent == node && !isHighlightOnDivider

        // Add dividers and highlight
        panel.border = object : javax.swing.border.Border {
            override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
                val g2d = g as Graphics2D
                // Draw highlight if this node or divider is highlighted
                if (isHighlightedNode) {
                    g2d.color = Color(0, 153, 255) // Bright blue for node highlight
                    g2d.stroke = BasicStroke(2f)
                    g2d.drawRect(x, y, width - 1, height - 1)
                } else if (isHighlightOnDivider && highlightedPath?.lastPathComponent == node) {
                    // Highlight divider above or below based on drop position
                    val dropPoint = myToolWindow.getDropPoint()
                    val dropY = if (dropPoint != null) {
                        val bounds = tree.getRowBounds(row)
                        if (bounds != null) {
                            val relativeY = dropPoint.y - bounds.y
                            val nodeHeight = bounds.height
                            relativeY < nodeHeight * 0.25 // Top divider if in top 25%
                        } else {
                            true // Default to top divider
                        }
                    } else {
                        true // Default to top divider
                    }
                    g2d.color = Color(0, 153, 255) // Bright blue for divider highlight
                    g2d.stroke = BasicStroke(2f)
                    if (dropY) {
                        g2d.drawLine(x, y, x + width, y) // Highlight top divider
                    } else {
                        g2d.drawLine(x, y + height - 1, x + width, y + height - 1) // Highlight bottom divider
                    }
                }
                // Draw regular dividers
                g2d.color = Color.GRAY
                g2d.stroke = BasicStroke(1f)

                // Determine if this is the first or last visible node
                val isFirst = row == 1 // First visible row (root is hidden)
                val isLast = row == tree.rowCount - 1

                // Draw top divider for first node or between nodes
                if (isFirst || row > 1) {
                    g2d.drawLine(x, y, x + width, y)
                }
                // Draw bottom divider for last node or between nodes
                if (isLast || row < tree.rowCount - 1) {
                    g2d.drawLine(x, y + height - 1, x + width, y + height - 1)
                }
            }

            override fun getBorderInsets(c: Component): Insets = Insets(2, 0, 2, 0)
            override fun isBorderOpaque(): Boolean = true
        }


        panel.add(label, BorderLayout.CENTER)
        return panel
    }
}