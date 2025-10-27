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
                val rootElement = document.rootElement
                // Parse tracePoints
                val tracePointsElement = rootElement.getChild("tracePoints")
                val tracePoints = tracePointsElement?.getChildren("tracePoint")?.map { element ->
                    TracePointService.TracePoint(
                        id = element.getChildText("id") ?: "",
                        name = element.getChildText("name") ?: "",
                        fileName = element.getChildText("fileName") ?: "",
                        filePath = element.getChildText("filePath") ?: "",
                        lineNumber = element.getChildText("lineNumber")?.toIntOrNull() ?: 0,
                        parentId = element.getChildText("parentId"),
                        projectPath = project.basePath ?: "",
                        lineContent = element.getChildText("lineContent"),
                        isValid = element.getChildText("isValid")?.toBoolean() ?: true,
                        totalOccurrences = element.getChildText("totalOccurrences")?.toIntOrNull() ?: 0,
                        occurrenceIndex = element.getChildText("occurrenceIndex")?.toIntOrNull() ?: 0,
                        description = element.getChildText("description") ?: ""
                    )
                } ?: emptyList()
                // Parse expandedTracePointIds
                val expandedIdsElement = rootElement.getChild("expandedTracePointIds")
                val expandedTracePointIds = expandedIdsElement?.getChildren("id")?.map { it.text } ?: emptyList()
                // Create TracePointState
                val state = TracePointService.TracePointState(
                    tracePoints = tracePoints,
                    expandedTracePointIds = expandedTracePointIds,
                    highlightingEnabled = true, // Default value
                    descriptionAreaOpened = false // Default value
                )
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