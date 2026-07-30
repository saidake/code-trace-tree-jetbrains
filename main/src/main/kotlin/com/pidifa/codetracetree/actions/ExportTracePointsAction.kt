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
import org.jdom.Element
import org.jdom.output.Format
import org.jdom.output.XMLOutputter
import java.io.File

/**
 * Export all trace-points in the **exact** format that the plugin stores internally.
 * The output XML looks like this:
 *
 * <tracePointState>
 *   <tracePointNodes>
 *     <tracePointNode>
 *       <id>…</id>
 *       <parentId>…</parentId>
 *       <tracePoint>…</tracePoint>
 *       <children>
 *         <tracePointNode>…</tracePointNode>
 *         …
 *       </children>
 *     </tracePointNode>
 *   </tracePointNodes>
 *   <expandedTracePointIds>…</expandedTracePointIds>
 * </tracePointState>
 */
class ExportTracePointsAction : AnAction(null, "Export Trace Points", AllIcons.Actions.Upload) {

    init {
        templatePresentation.text = "Export Trace Points"
        templatePresentation.description = "Export all trace points to an XML file"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = project.service<TracePointService>()

        // ---- ask for file name -------------------------------------------------
        val defaultName = "code-trace-tree-config.xml"
        val fileName = Messages.showInputDialog(
            project,
            "Enter file name for trace points export:",
            "Export Trace Points",
            null,
            defaultName,
            null
        )?.trim() ?: return

        val finalName = if (fileName.endsWith(".xml", ignoreCase = true)) fileName else "$fileName.xml"

        // ---- ask for directory -------------------------------------------------
        val descriptor = FileChooserDescriptor(false, true, false, false, false, false)
            .withTitle("Export Trace Points – Select Directory")
            .withDescription("Choose a directory to save the trace points file")

        FileChooser.chooseFile(descriptor, project, null) { directory ->
            val path = "${directory.path}/$finalName"

            // ---- build XML -------------------------------------------------------
            val state = service.getState()               // <-- already a TracePointState
            val rootElement = Element("tracePointState")

            // ----- root nodes ----------------------------------------------------
            val tracePointNodesEl = Element("tracePointNodes")
            state.tracePointNodes.forEach { exportNode(it, tracePointNodesEl) }
            rootElement.addContent(tracePointNodesEl)

            // ----- expanded ids --------------------------------------------------
            val expandedEl = Element("expandedTracePointIds")
            state.expandedTracePointIds.forEach { id ->
                expandedEl.addContent(Element("id").setText(id))
            }
            rootElement.addContent(expandedEl)

            // ----- write ---------------------------------------------------------
            val xml = XMLOutputter(Format.getPrettyFormat()).outputString(rootElement)
            File(path).writeText(xml, Charsets.UTF_8)

            Messages.showInfoMessage(project, "Trace points exported to $finalName", "Export Finished")
        }
    }

    /** Recursively serialize a TracePointNode → XML */
    private fun exportNode(node: TracePointService.TracePointNode, parentEl: Element) {
        val nodeEl = Element("tracePointNode")
        nodeEl.addContent(Element("id").setText(node.id))
        nodeEl.addContent(Element("parentId").setText(node.parentId))

        // <tracePoint>
        val tracePointEl = Element("tracePoint").apply {
            addContent(Element("name").setText(node.tracePoint.name))
            addContent(Element("fileName").setText(node.tracePoint.fileName))
            addContent(Element("filePath").setText(node.tracePoint.filePath))
            addContent(Element("lineNumber").setText(node.tracePoint.lineNumber.toString()))
            addContent(Element("projectPath").setText(node.tracePoint.projectPath))
            addContent(Element("lineContent").setText(node.tracePoint.lineContent ?: ""))
            addContent(Element("isValid").setText(node.tracePoint.isValid.toString()))
            addContent(Element("totalOccurrences").setText(node.tracePoint.totalOccurrences.toString()))
            addContent(Element("occurrenceIndex").setText(node.tracePoint.occurrenceIndex.toString()))
            addContent(Element("description").setText(node.tracePoint.description))
        }
        nodeEl.addContent(tracePointEl)

        // <children>
        if (node.children.isNotEmpty()) {
            val childrenEl = Element("children")
            node.children.forEach { exportNode(it, childrenEl) }
            nodeEl.addContent(childrenEl)
        }

        parentEl.addContent(nodeEl)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}