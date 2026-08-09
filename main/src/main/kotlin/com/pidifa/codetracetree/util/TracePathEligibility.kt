/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

/**
 * Eligibility for Code Trace Tree create/update/go-to actions.
 * Only local files/directories under the project root may receive a relative `tracePath`.
 */
object TracePathEligibility {
    fun isEligible(project: Project?, file: VirtualFile?): Boolean {
        if (project == null || file == null) return false
        if (!file.isInLocalFileSystem) return false
        val basePath = project.basePath ?: return false
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath) ?: return false
        return VfsUtilCore.isAncestor(projectRoot, file, false)
    }
}
