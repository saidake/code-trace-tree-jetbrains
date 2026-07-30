/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.File

/**
 * Export current profile (`<tracePointState>`) or all profiles (`<traceProfiles>`).
 */
class ExportTracePointsAction : AnAction(null, "Export Trace Points", AllIcons.Actions.Upload) {

    init {
        templatePresentation.text = "Export Trace Points"
        templatePresentation.description = "Export the current profile or all profiles to an XML file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()

        val exportChoice = Messages.showDialog(
            project,
            "Choose what to export.\n\n" +
                "• Current profile – exports \"${service.getActiveProfileName()}\" " +
                "(compatible with older imports).\n" +
                "• All profiles – exports every profile in a multi-profile file.",
            "Export Trace Points",
            arrayOf("Current Profile", "All Profiles", "Cancel"),
            0,
            Messages.getQuestionIcon()
        )
        if (exportChoice < 0 || exportChoice == 2) return
        val exportAll = exportChoice == 1

        val defaultName = if (exportAll) {
            "code-trace-tree-profiles.xml"
        } else {
            "code-trace-tree-${service.getActiveProfileName()}.xml"
        }
        val fileName = Messages.showInputDialog(
            project,
            "Enter file name for trace points export:",
            "Export Trace Points",
            null,
            defaultName,
            null
        )?.trim() ?: return

        val finalName = if (fileName.endsWith(".xml", ignoreCase = true)) fileName else "$fileName.xml"

        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Export Trace Points – Select Directory")
            .withDescription("Choose a directory to save the trace points file")

        FileChooser.chooseFile(descriptor, project, null) { directory ->
            val path = "${directory.path}/$finalName"
            val rootElement = if (exportAll) {
                TraceProfileXml.exportAllProfiles(
                    service.getProfilesSnapshot(),
                    service.getActiveProfileName()
                )
            } else {
                TraceProfileXml.exportSingleProfile(
                    service.getActiveProfileName(),
                    service.getTracePoints(),
                    service.getExpandedTracePointIds()
                )
            }

            val xml = XMLOutputter(Format.getPrettyFormat()).outputString(rootElement)
            File(path).writeText(xml, Charsets.UTF_8)

            val scope = if (exportAll) "all profiles" else "profile \"${service.getActiveProfileName()}\""
            Messages.showInfoMessage(project, "Exported $scope to $finalName", "Export Finished")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
