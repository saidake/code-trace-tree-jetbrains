// src/main/kotlin/com/simi/labs/codetracetree/actions/ImportTracePointsAction.kt
package com.simi.labs.codetracetree.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.ui.Messages
import com.simi.labs.codetracetree.services.TracePointService
import org.jdom.Element
import org.jdom.input.SAXBuilder
import java.io.File
import java.util.*

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
            .withDescription("Choose the exported trace-points XML file")
            .withFileFilter { it.extension?.lowercase() == "xml" }

        FileChooser.chooseFile(descriptor, project, null) { file ->
            try {
                val doc = SAXBuilder().build(file.path)
                val root = doc.rootElement

                if (root.name != "tracePointState") {
                    Messages.showErrorDialog(project, "Invalid file – root element must be <tracePointState>", "Import Failed")
                    return@chooseFile
                }

                // 1. Build the full TracePointState object (same shape as getState())
                val state = TracePointService.TracePointState().apply {
                    // root nodes (the tree)
                    val rootNodesEl = root.getChild("rootNodes") ?: run {
                        Messages.showWarningDialog(project, "No <rootNodes> element found", "Import Warning")
                        return@chooseFile
                    }
                    rootNodesEl.getChildren("tracePointNode").forEach { nodeEl ->
                        rootNodes.add(importNode(nodeEl, null))
                    }

                    // expanded ids
                    root.getChild("expandedTracePointIds")?.getChildren("id")?.forEach { idEl ->
                        val id = idEl.textTrim
                        if (id.isNotBlank()) expandedTracePointIds.add(id)
                    }

                    // keep defaults for the rest (or read them if you add them later)
                    highlightingEnabled = true
                    descriptionAreaOpened = false
                }

                // 2. Load the state exactly like the plugin does internally
                service.loadState(state)

                Messages.showInfoMessage(project, "Trace points imported successfully", "Import Finished")
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to import: ${ex.message}", "Import Error")
                ex.printStackTrace()
            }
        }
    }

    /** Recursively turn <tracePointNode> → TracePointNode */
    private fun importNode(
        nodeEl: Element,
        parentId: String?
    ): TracePointService.TracePointNode {

        val tpEl = nodeEl.getChild("tracePoint")?.getChild("tracePoint")
            ?: throw IllegalArgumentException("Missing <tracePoint> element")

        val tp = TracePointService.TracePoint(
            name = tpEl.getChildTextTrim("name") ?: "",
            fileName = tpEl.getChildTextTrim("fileName") ?: "",
            filePath = tpEl.getChildTextTrim("filePath") ?: "",
            lineNumber = tpEl.getChildTextTrim("lineNumber")?.toIntOrNull() ?: -1,
            projectPath = tpEl.getChildTextTrim("projectPath") ?: "",
            lineContent = tpEl.getChildTextTrim("lineContent") ?: "",
            isValid = tpEl.getChildTextTrim("isValid")?.toBoolean() ?: true,
            totalOccurrences = tpEl.getChildTextTrim("totalOccurrences")?.toIntOrNull() ?: 1,
            occurrenceIndex = tpEl.getChildTextTrim("occurrenceIndex")?.toIntOrNull() ?: 1,
            description = tpEl.getChildTextTrim("description") ?: ""
        )

        val node = TracePointService.TracePointNode(tpEl.getChildTextTrim("id") ?: UUID.randomUUID().toString(),tp)

        // children
        nodeEl.getChild("children")?.getChildren("tracePointNode")?.forEach { childEl ->
            node.children.add(importNode(childEl, node.id))
        }

        return node
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}