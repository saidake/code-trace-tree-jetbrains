package com.simi.labs.codetracetree.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.simi.labs.codetracetree.services.TracePointService

class UpdateTracePointAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val service = project.service<TracePointService>()
        val selectedTracePointIds = service.getTracePoints().filter { service.isTracePointSelected(it.id) }.map { it.id }

        if (selectedTracePointIds.isEmpty()) {
            Messages.showWarningDialog(
                project,
                "No trace points are selected in the Workflow Trace tool window.",
                "Update Trace Points"
            )
            return
        }

        val document = editor.document
        val lineNumber = editor.caretModel.currentCaret.logicalPosition.line + 1
        if (lineNumber > document.lineCount) {
            Messages.showWarningDialog(
                project,
                "Invalid line number: $lineNumber. The file has only ${document.lineCount} lines.",
                "Update Trace Points"
            )
            return
        }

        val startOffset = document.getLineStartOffset(lineNumber - 1)
        val endOffset = document.getLineEndOffset(lineNumber - 1)
        val lineContent = document.getText(com.intellij.openapi.util.TextRange(startOffset, endOffset)).trim()
        val projectPath = project.basePath ?: return
        val filePath = file.path.removePrefix("$projectPath/")
        val fileName = file.name

        val updatedTracePoints = service.getTracePoints().map { tracePoint ->
            if (tracePoint.id in selectedTracePointIds) {
                val (totalOccurrences, matchingLines) = service.getLineOccurrences(document, lineContent)
                val occurrenceIndex = matchingLines.indexOf(lineNumber) + 1
                tracePoint.copy(
                    fileName = fileName,
                    filePath = filePath,
                    projectPath = projectPath,
                    lineNumber = lineNumber,
                    lineContent = lineContent,
                    isValid = true,
                    totalOccurrences = totalOccurrences,
                    occurrenceIndex = if (occurrenceIndex >= 0) occurrenceIndex else 0
                )
            } else {
                tracePoint
            }
        }

        service.updateTracePoints(updatedTracePoints)
        service.selectTracePoints(selectedTracePointIds) // Preserve selection
        service.highlightTracePointsInFile(file) // Ensure highlights are applied
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = project?.let { FileEditorManager.getInstance(it).selectedTextEditor }
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabled = project != null && editor != null && file != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }
}