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
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.File

class ExportTracePointsAction : AnAction(null, "Export Trace Points", AllIcons.Actions.Upload) {
    init {
        templatePresentation.text = "Export Trace Points"
        templatePresentation.description = "Export all trace points to an XML file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val defaultFileName = "code-trace-tree-config.xml"

        // Prompt for file name
        val fileName = Messages.showInputDialog(
            project,
            "Enter file name for trace points export:",
            "Export Trace Points",
            null,
            defaultFileName,
            null
        )?.trim()

        // Exit if user cancels the dialog
        if (fileName.isNullOrBlank()) {
            return
        }

        // Ensure the file has .xml extension
        val finalFileName = if (fileName.endsWith(".xml", ignoreCase = true)) fileName else "$fileName.xml"

        // Prompt for directory
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Export Trace Points - Select Directory")
            .withDescription("Choose a directory to save the trace points file")
        FileChooser.chooseFile(descriptor, project, null) { directory ->
            val filePath = "${directory.path}/$finalFileName"
            val state = service.getState()
            // Create root element
            val rootElement = Element("tracePointState")
            // Add tracePoints element
            val tracePointsElement = Element("tracePoints")
            state.tracePoints.forEach { tracePoint ->
                val tracePointElement = Element("tracePoint").apply {
                    addContent(Element("id").setText(tracePoint.id))
                    addContent(Element("name").setText(tracePoint.name))
                    addContent(Element("filePath").setText(tracePoint.filePath))
                    addContent(Element("fileName").setText(tracePoint.fileName))
                    addContent(Element("lineNumber").setText(tracePoint.lineNumber.toString()))
                    if (tracePoint.parentId != null) {
                        addContent(Element("parentId").setText(tracePoint.parentId))
                    }
                    addContent(Element("projectPath").setText(tracePoint.projectPath))
                    addContent(Element("lineContent").setText(tracePoint.lineContent ?: ""))
                    addContent(Element("isValid").setText(tracePoint.isValid.toString()))
                    addContent(Element("totalOccurrences").setText(tracePoint.totalOccurrences.toString()))
                    addContent(Element("occurrenceIndex").setText(tracePoint.occurrenceIndex.toString()))
                    addContent(Element("description").setText(tracePoint.description))
                }
                tracePointsElement.addContent(tracePointElement)
            }
            rootElement.addContent(tracePointsElement)
            // Add expandedTracePointIds element
            val expandedIdsElement = Element("expandedTracePointIds")
            state.expandedTracePointIds.forEach { id ->
                expandedIdsElement.addContent(Element("id").setText(id))
            }
            rootElement.addContent(expandedIdsElement)
            // Write to file
            val xmlOutput = XMLOutputter(Format.getPrettyFormat())
            val xmlString = xmlOutput.outputString(rootElement)
            File(filePath).writeText(xmlString)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}