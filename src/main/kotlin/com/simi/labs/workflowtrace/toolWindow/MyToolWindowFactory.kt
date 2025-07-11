package com.simi.labs.workflowtrace.toolWindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBList
import com.intellij.ui.content.ContentFactory
import com.simi.labs.workflowtrace.services.TracePointService
import javax.swing.DefaultListModel

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
            service.addTracePointListener { tracePoints ->
                listModel.clear()
                tracePoints.forEach { listModel.addElement(it) }
            }
            cellRenderer = TracePointListRenderer()
            addListSelectionListener { e ->
                if (!e.valueIsAdjusting) {
                    selectedValue?.let { tracePoint ->
                        tracePoint.navigateTo()
                    }
                }
            }
        }
    }
}