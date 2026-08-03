/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.pidifa.codetracetree.services.TracePointService
import org.jdom.input.SAXBuilder

/**
 * Import single-profile (`<traceProfile>`) or multi-profile (`<traceProfiles>`) files.
 * Always asks how to apply the data — never auto-overwrites.
 */
class ImportTracePointsAction : AnAction(null, "Import Trace Points", AllIcons.Actions.Download) {

    init {
        templatePresentation.text = "Import Trace Points"
        templatePresentation.description = "Import trace points from an XML file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()

        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withTitle("Import Trace Points – Select XML File")
            .withDescription("Choose a single-profile or multi-profile export file")
            .withFileFilter { it.extension?.lowercase() == "xml" }

        FileChooser.chooseFile(descriptor, project, null) { file ->
            try {
                val root = SAXBuilder().build(file.path).rootElement
                when (root.name) {
                    TraceProfileXml.ROOT_SINGLE -> importSingle(project, service, TraceProfileXml.parseSingle(root))
                    TraceProfileXml.ROOT_MULTI -> importMulti(project, service, TraceProfileXml.parseMulti(root))
                    else -> Messages.showErrorDialog(
                        project,
                        "Invalid file – root element must be <${TraceProfileXml.ROOT_SINGLE}> " +
                            "or <${TraceProfileXml.ROOT_MULTI}>",
                        "Import Failed"
                    )
                }
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to import: ${ex.message}", "Import Error")
            }
        }
    }

    private fun importSingle(
        project: Project,
        service: TracePointService,
        parsed: TraceProfileXml.ParsedSingle
    ) {
        val suggestedName = parsed.profileName ?: "imported"
        val choice = Messages.showDialog(
            project,
            "This file contains a single profile" +
                (parsed.profileName?.let { " (\"$it\")" } ?: "") + ".\n\n" +
                "• Replace Current Profile – overwrite \"${service.getActiveProfileName()}\".\n" +
                "• Import as New Profile – keep existing profiles and add a new one.",
            "Import Trace Points",
            arrayOf("Replace Current Profile", "Import as New Profile", "Cancel"),
            0,
            Messages.getQuestionIcon()
        )
        when (choice) {
            0 -> {
                service.replaceActiveProfileTree(parsed.nodes, parsed.expandedIds)
                Messages.showInfoMessage(
                    project,
                    "Replaced profile \"${service.getActiveProfileName()}\".",
                    "Import Finished"
                )
            }
            1 -> {
                val name = service.importAsNewProfile(suggestedName, parsed.nodes, parsed.expandedIds)
                Messages.showInfoMessage(project, "Imported as new profile \"$name\".", "Import Finished")
            }
            else -> return
        }
    }

    private fun importMulti(
        project: Project,
        service: TracePointService,
        parsed: TraceProfileXml.ParsedMulti
    ) {
        val names = parsed.profiles.joinToString(", ") { "\"${it.name}\"" }
        val choice = Messages.showDialog(
            project,
            "This file contains ${parsed.profiles.size} profile(s): $names.\n\n" +
                "• Import as New Profiles – add all; rename on name conflicts.\n" +
                "• Merge All Profiles – overwrite same-named profiles; add the rest; keep local-only profiles.\n" +
                "• Replace All Profiles – discard local profiles and use the file’s profiles.",
            "Import Trace Points",
            arrayOf("Import as New Profiles", "Merge All Profiles", "Replace All Profiles", "Cancel"),
            0,
            Messages.getQuestionIcon()
        )
        when (choice) {
            0 -> {
                val created = service.importAsNewProfiles(parsed.profiles)
                Messages.showInfoMessage(
                    project,
                    "Imported ${created.size} profile(s): ${created.joinToString(", ") { "\"$it\"" }}.",
                    "Import Finished"
                )
            }
            1 -> {
                service.mergeProfiles(parsed.profiles, parsed.activeProfileName)
                Messages.showInfoMessage(
                    project,
                    "Merged ${parsed.profiles.size} profile(s) into the project.",
                    "Import Finished"
                )
            }
            2 -> {
                val confirm = Messages.showYesNoDialog(
                    project,
                    "This will delete all existing local profiles and replace them with the file’s profiles. Continue?",
                    "Replace All Profiles",
                    null
                )
                if (confirm != Messages.YES) return
                service.replaceAllProfiles(parsed.profiles, parsed.activeProfileName)
                Messages.showInfoMessage(
                    project,
                    "Replaced all profiles with ${parsed.profiles.size} profile(s) from the file.",
                    "Import Finished"
                )
            }
            else -> return
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
