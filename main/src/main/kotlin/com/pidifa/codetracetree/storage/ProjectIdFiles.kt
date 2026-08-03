/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Reads/writes the local project id under `.idea/` (preferred for IntelliJ)
 * with fallback to `.vscode/` when the IDE-native file is missing.
 */
object ProjectIdFiles {
    const val IDEA_FILE = "code-trace-tree.project.id"
    const val VSCODE_FILE = "code-trace-tree.project.id"

    fun ideaIdPath(projectBase: Path): Path =
        projectBase.resolve(".idea").resolve(IDEA_FILE)

    fun vscodeIdPath(projectBase: Path): Path =
        projectBase.resolve(".vscode").resolve(VSCODE_FILE)

    /**
     * Prefer `.idea/code-trace-tree.project.id`; if missing, use
     * `.vscode/code-trace-tree.project.id` when present.
     */
    fun readProjectId(projectBase: Path): String? {
        val ideaId = readIdFile(ideaIdPath(projectBase))
        if (!ideaId.isNullOrBlank()) return ideaId
        return readIdFile(vscodeIdPath(projectBase))
    }

    /** Writes the project id only to `.idea/` (current IDE). */
    fun writeProjectId(projectBase: Path, projectId: String) {
        val path = ideaIdPath(projectBase)
        Files.createDirectories(path.parent)
        Files.writeString(path, projectId.trim() + "\n", StandardCharsets.UTF_8)
    }

    private fun readIdFile(path: Path): String? {
        if (!Files.isRegularFile(path)) return null
        return Files.readString(path, StandardCharsets.UTF_8).trim().takeIf { it.isNotEmpty() }
    }
}
