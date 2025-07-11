package com.simi.labs.workflowtrace.toolWindow

import com.intellij.ui.components.JBList
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.Color
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class TracePointListRenderer(private val service: TracePointService) : ListCellRenderer<TracePointService.TracePoint> {
    override fun getListCellRendererComponent(
        list: JList<out TracePointService.TracePoint>,
        value: TracePointService.TracePoint,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val label = JLabel("${value.name} (${value.fileName}:${value.lineNumber})")
        // Use persistent selection state from service
        val isPersistentlySelected = service.isTracePointSelected(value.id)
        if (isPersistentlySelected) {
            label.background = Color(0, 120, 215) // Custom selected background (blue)
            label.foreground = Color.WHITE // White text for contrast
        } else {
            label.background = list.background
            label.foreground = list.foreground
        }
        label.isOpaque = true
        return label
    }
}