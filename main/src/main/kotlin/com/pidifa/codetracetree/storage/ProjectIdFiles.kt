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
