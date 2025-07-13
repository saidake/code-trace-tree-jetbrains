package com.simi.labs.workflowtrace.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.simi.labs.workflowtrace.services.TracePointService
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.util.xmlb.XmlSerializer
import org.jdom.output.XMLOutputter
import org.jdom.output.Format
import java.io.File

class ExportTracePointsAction : AnAction(null, "Export Trace Points", AllIcons.Actions.Upload) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()
        val descriptor = FileChooserDescriptor(false, false, false, false, false, false)
            .withFileFilter { it.extension == "xml" }
            .withTitle("Export Trace Points")
        FileChooser.chooseFile(descriptor, project, null) { file ->
            val state = service.getState()
            val element = XmlSerializer.serialize(state)
            val xmlOutput = XMLOutputter(Format.getPrettyFormat())
            val xmlString = xmlOutput.outputString(element)
            File(file.path).writeText(xmlString)
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}
