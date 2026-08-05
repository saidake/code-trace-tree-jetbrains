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
 * Global agent notify signals under `<appDir>/signals/`.
 *
 * - `<projectId>.request_refresh` — full project reload (all profiles + toolbar flags)
 * - `<projectId>.request_refresh_profile` — one profile; body = profile name
 *   (empty / missing name → active profile). Does not change activeProfileName or flags.
 * - `<projectId>.select_trace_points`
 * - `<projectId>.storage-ready` — Case C bind handshake (no TTL; agent overwrites)
 *
 * Refresh/select files older than [TTL_MS] are ignored and deleted so a late IDE open
 * does not replay a stale select/refresh. Fresh refresh/select signals are left in place
 * so every open IDE window for the same projectId can observe them; agents overwrite on
 * the next notify.
 */
object AgentSignalFiles {
    const val SIGNALS_DIR_NAME = "signals"
    const val REFRESH_SUFFIX = ".request_refresh"
    const val REFRESH_PROFILE_SUFFIX = ".request_refresh_profile"
    const val SELECT_SUFFIX = ".select_trace_points"
    const val STORAGE_READY_SUFFIX = ".storage-ready"
    /** Ignore / delete refresh and select signal files older than this age. */
    const val TTL_MS = 60_000L

    fun signalsDir(): Path = GlobalStoragePaths.resolveAppDir().resolve(SIGNALS_DIR_NAME)

    fun ensureSignalsDir(): Path {
        val dir = signalsDir()
        Files.createDirectories(dir)
        return dir
    }

    fun refreshFileName(projectId: String): String = "$projectId$REFRESH_SUFFIX"

    fun refreshProfileFileName(projectId: String): String = "$projectId$REFRESH_PROFILE_SUFFIX"

    fun selectFileName(projectId: String): String = "$projectId$SELECT_SUFFIX"

    fun storageReadyFileName(projectId: String): String = "$projectId$STORAGE_READY_SUFFIX"

    fun refreshPath(projectId: String): Path = signalsDir().resolve(refreshFileName(projectId))

    fun refreshProfilePath(projectId: String): Path =
        signalsDir().resolve(refreshProfileFileName(projectId))

    fun selectPath(projectId: String): Path = signalsDir().resolve(selectFileName(projectId))

    fun storageReadyPath(projectId: String): Path =
        signalsDir().resolve(storageReadyFileName(projectId))

    /** Parse projectId from `<projectId>.storage-ready`; null if not that pattern. */
    fun projectIdFromStorageReadyFileName(fileName: String): String? {
        if (!fileName.endsWith(STORAGE_READY_SUFFIX)) return null
        val id = fileName.removeSuffix(STORAGE_READY_SUFFIX)
        return id.takeIf { it.isNotBlank() }
    }

    /** First non-empty trimmed line of a profile-refresh signal (may be ""). */
    fun readProfileRefreshName(path: Path): String {
        if (!Files.isRegularFile(path)) return ""
        return try {
            Files.readAllLines(path, StandardCharsets.UTF_8)
                .map { it.trim() }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Returns true when [path] exists and is within TTL.
     * Stale files are deleted and yield false.
     * Do not use for [STORAGE_READY_SUFFIX] (no TTL).
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

    fun exists(path: Path): Boolean = Files.isRegularFile(path)

    fun deleteQuietly(path: Path) {
        try {
            Files.deleteIfExists(path)
        } catch (_: Exception) {
        }
    }
}
