/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

/** LINE-tip fields used by rebind and typing-shift (no IntelliJ dependency). */
data class LineAnchor(
    val lineNumber: Int,
    val lineContent: String?,
    val isValid: Boolean,
    val totalOccurrences: Int,
    val occurrenceIndex: Int,
)

fun matchingLineNumbers(lines: List<String>, content: String): List<Int> {
    val matches = mutableListOf<Int>()
    for (i in lines.indices) {
        if (lines[i].trim() == content) matches.add(i + 1)
    }
    return matches
}
