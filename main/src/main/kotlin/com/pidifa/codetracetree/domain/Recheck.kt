/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

/**
 * Toolbar Recheck / load validate: LINE uses content rebind; FILE/DIRECTORY use path kind.
 * File-open and external-editor reload use the same LINE path with the current buffer.
 */
object Recheck {
    fun recheckTrace(
        kind: RecheckKind,
        tip: LineAnchor,
        exists: Boolean,
        isDirectory: Boolean,
        lines: List<String>? = null,
    ): LineAnchor {
        if (kind == RecheckKind.LINE) {
            if (!exists || isDirectory || lines == null) {
                return tip.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
            }
            return LineRebind.apply(tip, lines)
        }
        val pathKind = if (kind == RecheckKind.DIRECTORY) PathKind.DIRECTORY else PathKind.FILE
        return tip.copy(
            isValid = LineRebind.pathTraceIsValid(pathKind, exists, isDirectory),
            totalOccurrences = 0,
            occurrenceIndex = 0,
        )
    }
}

enum class RecheckKind { LINE, FILE, DIRECTORY }
