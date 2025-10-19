package com.simi.labs.workflowtrace.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.output.XMLOutputter
import org.jdom.output.Format
import java.io.File

class ExportTracePointsAction : AnAction(null, "Export Trace Points", AllIcons.Actions.Upload) {
    init {
        templatePresentation.text = "Export Trace Points"
        templatePresentation.description = "Export all trace points to an XML file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val defaultFileName = "workflowTrace.xml"

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
            val element = XmlSerializer.serialize(state)
            val xmlOutput = XMLOutputter(Format.getPrettyFormat())
            val xmlString = xmlOutput.outputString(element)
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