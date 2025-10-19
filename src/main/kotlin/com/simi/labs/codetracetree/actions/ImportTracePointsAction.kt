package com.simi.labs.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.codetracetree.services.TracePointService
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.input.SAXBuilder
import java.io.File
import java.io.StringReader

class ImportTracePointsAction : AnAction(null, "Import Trace Points", AllIcons.Actions.Download) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension == "xml" }
            .withTitle("Import Trace Points")
        FileChooser.chooseFile(descriptor, project, null) { file ->
            try {
                val xmlString = File(file.path).readText()
                val builder = SAXBuilder()
                val document = builder.build(StringReader(xmlString))
                val element = document.rootElement
                val state = XmlSerializer.deserialize(element, TracePointService.TracePointState::class.java)
                val currentProjectPath = project.basePath ?: ""
                val updatedTracePoints = state.tracePoints.map { tracePoint ->
                    tracePoint.copy(projectPath = currentProjectPath)
                }
                state.tracePoints = updatedTracePoints;
                service.loadState(state)
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to import trace points: ${ex.message}", "Import Error")
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
