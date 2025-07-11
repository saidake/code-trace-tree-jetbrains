package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.simi.labs.workflowtrace.services.TracePointService
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.ListSelectionModel

class MyToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val myToolWindow = MyToolWindow(toolWindow)
        val content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class MyToolWindow(toolWindow: ToolWindow) {
        private val service = toolWindow.project.service<TracePointService>()
        private val listModel = DefaultListModel<TracePointService.TracePoint>()

        fun getContent() = JBList<TracePointService.TracePoint>(listModel).apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            service.addTracePointListener { tracePoints ->
                thisLogger().info("Updating tool window with ${tracePoints.size} trace points")
                listModel.clear()
                tracePoints.forEach { listModel.addElement(it) }
                // Restore persistent selections
                val selectedIndices = tracePoints
                    .mapIndexedNotNull { index, tracePoint ->
                        if (service.isTracePointSelected(tracePoint.id)) index else null
                    }
                if (selectedIndices.isNotEmpty()) {
                    setSelectedIndices(selectedIndices.toIntArray())
                }
            }
            cellRenderer = TracePointListRenderer(service)
            addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    val index = locationToIndex(e.point)
                    if (index < 0) return
                    val tracePoint = listModel.getElementAt(index) ?: return
                    thisLogger().info("Mouse clicked on trace point: ${tracePoint.name} in ${tracePoint.fileName} at line ${tracePoint.lineNumber}")

                    if (e.clickCount == 1) {
                        // Single click: Toggle selection based on Ctrl
                        if (e.isControlDown) {
                            service.toggleTracePointSelection(tracePoint.id)
                        } else {
                            // Clear previous selections and select only this trace point
                            service.selectTracePoints(listOf(tracePoint.id))
                        }
                    } else if (e.clickCount == 2) {
                        // Double click: Navigate and select
                        thisLogger().info("Double-clicked trace point: ${tracePoint.name}")
                        tracePoint.navigateTo()
                        if (!e.isControlDown) {
                            service.selectTracePoints(listOf(tracePoint.id))
                        } else {
                            service.toggleTracePointSelection(tracePoint.id)
                        }
                    }
                }
            })
        }
    }
}