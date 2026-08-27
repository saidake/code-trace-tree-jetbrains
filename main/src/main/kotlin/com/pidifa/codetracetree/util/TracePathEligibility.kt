/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Eligibility for Code Trace Tree create/update/go-to actions and highlights.
 * Only local files/directories under the project root may receive a relative `tracePath`.
 * Git / IDE diff editors (both panes) are never eligible.
 */
object TracePathEligibility {
    fun isEligible(project: Project?, file: VirtualFile?): Boolean {
        if (project == null || file == null) return false
        if (!file.isInLocalFileSystem) return false
        val basePath = project.basePath ?: return false
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return false
        return VfsUtilCore.isAncestor(projectRoot, file, false)
    }

    fun isDiffEditor(editor: Editor?): Boolean {
        if (editor == null) return false
        if (editor.editorKind == EditorKind.DIFF) return true
        var component: java.awt.Component? = editor.component.parent
        while (component != null) {
            val name = component.javaClass.name
            if (
                name.startsWith("com.intellij.diff.") ||
                name.contains("DiffRequestProcessor") ||
                name.contains("DiffPreview") ||
                name.contains("ChangeViewDiff") ||
                name.contains("EditorTabDiff")
            ) {
                return true
            }
            component = component.parent
        }
        return false
    }

    fun isEligibleEditor(project: Project?, file: VirtualFile?, editor: Editor?): Boolean =
        editor != null && !isDiffEditor(editor) && isEligible(project, file)
}
