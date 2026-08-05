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
 * Reads/writes the local project id under `.idea/code-trace-tree.project.id`.
 */
object ProjectIdFiles {
    const val IDEA_FILE = "code-trace-tree.project.id"

    fun ideaIdPath(projectBase: Path): Path =
        projectBase.resolve(".idea").resolve(IDEA_FILE)

    fun readProjectId(projectBase: Path): String? =
        readIdFile(ideaIdPath(projectBase))

    /** Writes the project id to `.idea/`. */
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
