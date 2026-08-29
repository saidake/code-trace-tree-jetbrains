/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

sealed class TypingShiftResult {
    data object Skip : TypingShiftResult()
    data object RebindAll : TypingShiftResult()
    data class Update(val tip: LineAnchor) : TypingShiftResult()
}

object DocumentChange {
    /**
     * Bulk / agent rewrite from file start — offset math would collapse tips to line 1.
     * JetBrains DocumentEvent is always a single span.
     */
    fun shouldContentRebindDocumentEvent(
        offset: Int,
        oldLength: Int,
        newFragment: CharSequence,
        documentTextLength: Int,
        oldFragment: CharSequence,
    ): Boolean {
        if (offset != 0) return false
        val oldDocLen = documentTextLength - newFragment.length + oldLength
        if (oldDocLen > 0 && oldLength == oldDocLen) return true
        val oldLineBreaks = oldFragment.count { it == '\n' }
        val newLineBreaks = newFragment.count { it == '\n' }
        return oldLineBreaks >= 1 || newLineBreaks >= 2
    }

    /**
     * Incremental typing: Enter at line start, edit on the tip line, or insert/delete above.
     * Edit-on-tip-line applies even when lineOffset != 0 (Enter in the middle of the line).
     */
    fun applyTypingLineShift(
        tp: LineAnchor,
        newLines: List<String>,
        lineOffset: Int,
        changedLine: Int,
        isEnterAtLineStart: Boolean,
    ): TypingShiftResult {
        if (!tp.isValid) {
            val valid = newLines.getOrNull(tp.lineNumber - 1)?.trim() == tp.lineContent?.trim()
            return if (valid) TypingShiftResult.Update(tp.copy(isValid = true)) else TypingShiftResult.Skip
        }

        if (tp.lineNumber == changedLine && isEnterAtLineStart && lineOffset > 0) {
            val newLineNumber = tp.lineNumber + lineOffset
            val content = tp.lineContent?.trim().orEmpty()
            val matches = matchingLineNumbers(newLines, content)
            val occIdx = matches.indexOf(newLineNumber) + 1
            return TypingShiftResult.Update(
                tp.copy(
                    lineNumber = newLineNumber,
                    isValid = occIdx > 0,
                    totalOccurrences = matches.size,
                    occurrenceIndex = occIdx.coerceAtLeast(0),
                )
            )
        }

        if (tp.lineNumber == changedLine) {
            val newContent = newLines.getOrNull(changedLine - 1)?.trim()
            val matches = matchingLineNumbers(newLines, newContent.orEmpty())
            val occIdx =
                if (newContent == tp.lineContent) tp.occurrenceIndex else matches.indexOf(changedLine) + 1
            return TypingShiftResult.Update(
                tp.copy(
                    lineContent = newContent,
                    isValid = newContent != null,
                    totalOccurrences = matches.size,
                    occurrenceIndex = occIdx.coerceAtLeast(0),
                )
            )
        }

        if (tp.lineNumber > changedLine && lineOffset != 0) {
            val newLineNumber = tp.lineNumber + lineOffset
            if (newLineNumber < 1) return TypingShiftResult.RebindAll
            val content = tp.lineContent?.trim().orEmpty()
            val matches = matchingLineNumbers(newLines, content)
            val occIdx = matches.indexOf(newLineNumber) + 1
            val stillThere = occIdx > 0
            return TypingShiftResult.Update(
                tp.copy(
                    lineNumber = if (stillThere) newLineNumber else tp.lineNumber,
                    isValid = stillThere,
                    totalOccurrences = matches.size,
                    occurrenceIndex = if (stillThere) occIdx else 0,
                )
            )
        }

        return TypingShiftResult.Skip
    }
}
