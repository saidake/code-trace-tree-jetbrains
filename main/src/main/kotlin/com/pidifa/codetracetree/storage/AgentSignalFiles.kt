/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import java.nio.file.Files
import java.nio.file.Path

/**
 * Global agent notify signals under `<appDir>/signals/`.
 *
 * - `<projectId>.request_refresh`
 * - `<projectId>.select_trace_points`
 *
 * Files older than [TTL_MS] are ignored and deleted so a late IDE open does not
 * replay a stale select/refresh. Fresh signals are left in place so every open
 * IDE window for the same projectId can observe them; agents overwrite on the
 * next notify.
 */
object AgentSignalFiles {
    const val SIGNALS_DIR_NAME = "signals"
    const val REFRESH_SUFFIX = ".request_refresh"
    const val SELECT_SUFFIX = ".select_trace_points"
    /** Ignore / delete signal files older than this age. */
    const val TTL_MS = 60_000L

    fun signalsDir(): Path = GlobalStoragePaths.resolveAppDir().resolve(SIGNALS_DIR_NAME)

    fun refreshFileName(projectId: String): String = "$projectId$REFRESH_SUFFIX"

    fun selectFileName(projectId: String): String = "$projectId$SELECT_SUFFIX"

    fun refreshPath(projectId: String): Path = signalsDir().resolve(refreshFileName(projectId))

    fun selectPath(projectId: String): Path = signalsDir().resolve(selectFileName(projectId))

    /**
     * Returns true when [path] exists and is within TTL.
     * Stale files are deleted and yield false.
     */
    fun isFresh(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        val ageMs = try {
            System.currentTimeMillis() - Files.getLastModifiedTime(path).toMillis()
        } catch (_: Exception) {
            deleteQuietly(path)
            return false
        }
        if (ageMs > TTL_MS) {
            deleteQuietly(path)
            return false
        }
        return true
    }

    fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: Exception) {
        }
    }
}
