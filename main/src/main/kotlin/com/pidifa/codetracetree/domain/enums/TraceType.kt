/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain.enums

/**
 * What a trace point anchors to in the project.
 * - [LINE]: a specific source line (editor caret)
 * - [FILE]: a whole file (Project View)
 * - [DIRECTORY]: a directory (Project View)
 */
enum class TraceType {
    LINE,
    FILE,
    DIRECTORY;

    companion object {
        fun fromXml(value: String?): TraceType {
            if (value.isNullOrBlank()) return LINE
            return entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) } ?: LINE
        }
    }
}
