package com.simi.labs.workflowtrace.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import java.util.UUID

@Service(Service.Level.PROJECT)
class TracePointService(private val project: Project) {
    data class TracePoint(
        val id: String,
        val name: String,
        val file: VirtualFile,
        val lineNumber: Int,
        val project: Project
    ) {
        val fileName: String get() = file.name
        fun navigateTo() {
            FileEditorManager.getInstance(project).openFile(file, true)
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val psiFile = PsiManager.getInstance(project).findFile(file)
            editor?.caretModel?.moveToOffset(
                psiFile?.let { PsiDocumentManager.getInstance(project).getDocument(it) }
                    ?.getLineStartOffset(lineNumber - 1) ?: 0
            )
        }
    }

    private val tracePoints = mutableListOf<TracePoint>()
    private val listeners = mutableListOf<(List<TracePoint>) -> Unit>()
    private val highlighters = mutableMapOf<String, com.intellij.openapi.editor.markup.TextAttributes>()

    fun addTracePoint(name: String, file: VirtualFile, lineNumber: Int, editor: Editor) {
        val tracePoint = TracePoint(UUID.randomUUID().toString(), name, file, lineNumber, project)
        tracePoints.add(tracePoint)

        // Add visual marker
        val textAttributes = com.intellij.openapi.editor.markup.TextAttributes()
        textAttributes.backgroundColor = java.awt.Color.YELLOW
        editor.markupModel.addLineHighlighter(lineNumber - 1, HighlighterLayer.WARNING, textAttributes)
        highlighters[tracePoint.id] = textAttributes

        notifyListeners()
    }

    fun getTracePoints(): List<TracePoint> = tracePoints.toList()

    fun addTracePointListener(listener: (List<TracePoint>) -> Unit) {
        listeners.add(listener)
        listener(tracePoints)
    }

    private fun notifyListeners() {
        listeners.forEach { it(tracePoints) }
    }
}