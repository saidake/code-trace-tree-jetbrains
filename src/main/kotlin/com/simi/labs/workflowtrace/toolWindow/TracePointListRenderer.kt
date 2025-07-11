package com.simi.labs.workflowtrace.toolWindow

import com.intellij.ui.components.JBList
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.Component
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.ListCellRenderer

class TracePointListRenderer : ListCellRenderer<TracePointService.TracePoint> {
    override fun getListCellRendererComponent(
        list: JList<out TracePointService.TracePoint>,
        value: TracePointService.TracePoint,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val label = JLabel("${value.name} (${value.fileName}:${value.lineNumber})")
        if (isSelected) {
            label.background = list.selectionBackground
            label.foreground = list.selectionForeground
        } else {
            label.background = list.background
            label.foreground = list.foreground
        }
        label.isOpaque = true
        return label
    }
}