/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.storage

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Resolves the OS-specific base directory for global Code Trace Tree storage.
 *
 * - Windows: `%LOCALAPPDATA%` (fallback: `<UserHome>/AppData/Local`)
 * - macOS: `<UserHome>/Library/Application Support`
 * - Linux/Unix: `$XDG_CONFIG_HOME` (fallback: `<UserHome>/.config`)
 */
object GlobalStoragePaths {
    const val APP_DIR_NAME = "code-trace-tree"

    fun resolveBaseDir(): Path {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val userHome = System.getProperty("user.home")
            ?: throw IllegalStateException("user.home is not set")

        return when {
            os.contains("win") -> {
                val localAppData = System.getenv("LOCALAPPDATA")
                if (!localAppData.isNullOrBlank()) {
                    Paths.get(localAppData)
                } else {
                    Paths.get(userHome, "AppData", "Local")
                }
            }
            os.contains("mac") -> Paths.get(userHome, "Library", "Application Support")
            else -> {
                val xdg = System.getenv("XDG_CONFIG_HOME")
                if (!xdg.isNullOrBlank()) {
                    Paths.get(xdg)
                } else {
                    Paths.get(userHome, ".config")
                }
            }
        }
    }

    fun resolveAppDir(): Path = resolveBaseDir().resolve(APP_DIR_NAME)
}
