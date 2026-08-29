/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

import kotlin.math.abs

/**
 * Content-based LINE rebind (file open, Recheck, external reload).
 * 1 exact line, 2 unique content, 3 stable occurrence, 4 nearest match, else invalid.
 */
object LineRebind {
    fun apply(tp: LineAnchor, lines: List<String>): LineAnchor {
        val content = tp.lineContent?.trim()
        if (content.isNullOrEmpty()) {
            return tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
        }
        val matches = matchingLineNumbers(lines, content)
        val total = matches.size
        if (total == 0) {
            return tp.copy(isValid = false, totalOccurrences = 0, occurrenceIndex = 0)
        }
        val oldLine = tp.lineNumber
        val (newLine, newIndex) = when {
            oldLine in 1..lines.size && lines[oldLine - 1].trim() == content -> {
                oldLine to (matches.indexOf(oldLine) + 1)
            }
            total == 1 -> matches[0] to 1
            total == tp.totalOccurrences && tp.occurrenceIndex in 1..total -> {
                matches[tp.occurrenceIndex - 1] to tp.occurrenceIndex
            }
            else -> {
                val nearest = matches.minBy { abs(it - oldLine) }
                nearest to (matches.indexOf(nearest) + 1)
            }
        }
        return tp.copy(
            lineNumber = newLine,
            totalOccurrences = total,
            occurrenceIndex = newIndex,
            isValid = true
        )
    }

    /** FILE exists and is not a directory; DIRECTORY exists and is a directory. */
    fun pathTraceIsValid(kind: PathKind, exists: Boolean, isDirectory: Boolean): Boolean {
        if (!exists) return false
        return if (kind == PathKind.DIRECTORY) isDirectory else !isDirectory
    }
}

enum class PathKind { FILE, DIRECTORY }
