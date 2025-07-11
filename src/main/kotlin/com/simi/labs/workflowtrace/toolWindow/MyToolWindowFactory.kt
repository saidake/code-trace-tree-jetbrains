package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.simi.labs.workflowtrace.services.TracePointService
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
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            service.addTracePointListener { tracePoints ->
                thisLogger().info("Updating tool window with ${tracePoints.size} trace points")
                listModel.clear()
                tracePoints.forEach { listModel.addElement(it) }
            }
            cellRenderer = TracePointListRenderer()
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    val selected = selectedValue
                    if (selected != null) {
                        thisLogger().info("Trace point selected: ${selected.name} in ${selected.fileName} at line ${selected.lineNumber}")
                        selected.navigateTo()
                        // Clear selection to ensure subsequent clicks work
                        clearSelection()
                    } else {
                        thisLogger().warn("No trace point selected")
                    }
                }
            }
        }
    }
}