/*
 * Copyright (C) 2025-2026 Code Trace Tree Contributors
 *
 * SPDX-License-Identifier: MIT
 */
package com.pidifa.codetracetree.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DocumentChangeTest {
    private fun tip(
        lineNumber: Int,
        content: String? = "target()",
        isValid: Boolean = true,
        totalOccurrences: Int = 1,
        occurrenceIndex: Int = 1,
    ) = LineAnchor(lineNumber, content, isValid, totalOccurrences, occurrenceIndex)

    @Test
    fun `external full replace from offset 0 content-rebinds`() {
        val newText = "alpha\nbeta\n"
        assertTrue(
            DocumentChange.shouldContentRebindDocumentEvent(
                offset = 0,
                oldLength = 20,
                newFragment = newText,
                documentTextLength = newText.length,
                oldFragment = "old-file-contents....",
            )
        )
    }

    @Test
    fun `multi-line insert from file start content-rebinds (agent rewrite)`() {
        assertTrue(
            DocumentChange.shouldContentRebindDocumentEvent(
                offset = 0,
                oldLength = 0,
                newFragment = "one\ntwo\nthree\n",
                documentTextLength = 14,
                oldFragment = "",
            )
        )
    }

    @Test
    fun `single-line type in the middle of the file does not content-rebind`() {
        assertFalse(
            DocumentChange.shouldContentRebindDocumentEvent(
                offset = 40,
                oldLength = 0,
                newFragment = "x",
                documentTextLength = 50,
                oldFragment = "",
            )
        )
    }

    @Test
    fun `after an external rewrite unique content is rebound to the new line`() {
        val rebound = LineRebind.apply(tip(2), listOf("header()", "other()", "target()"))
        assertEquals(3, rebound.lineNumber)
        assertTrue(rebound.isValid)
    }

    @Test
    fun `Enter at the start of the tip line shifts the line index down`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(1),
            listOf("", "target()", "tail()"),
            lineOffset = 1,
            changedLine = 1,
            isEnterAtLineStart = true,
        )
        val update = result as TypingShiftResult.Update
        assertEquals(2, update.tip.lineNumber)
        assertEquals("target()", update.tip.lineContent)
        assertTrue(update.tip.isValid)
    }

    @Test
    fun `editing the tip line updates lineContent`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(1),
            listOf("target(1)", "tail()"),
            lineOffset = 0,
            changedLine = 1,
            isEnterAtLineStart = false,
        )
        val update = result as TypingShiftResult.Update
        assertEquals(1, update.tip.lineNumber)
        assertEquals("target(1)", update.tip.lineContent)
    }

    @Test
    fun `inserting a line above the tip increases lineNumber`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(2),
            listOf("new()", "header()", "target()"),
            lineOffset = 1,
            changedLine = 1,
            isEnterAtLineStart = false,
        )
        val update = result as TypingShiftResult.Update
        assertEquals(3, update.tip.lineNumber)
        assertEquals("target()", update.tip.lineContent)
        assertTrue(update.tip.isValid)
    }

    @Test
    fun `Enter in the middle of the tip line updates content on the original line`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(1, content = "target()"),
            listOf("tar", "get()", "tail()"),
            lineOffset = 1,
            changedLine = 1,
            isEnterAtLineStart = false,
        )
        val update = result as TypingShiftResult.Update
        assertEquals(1, update.tip.lineNumber)
        assertEquals("tar", update.tip.lineContent)
    }

    @Test
    fun `delete above that would move the tip before line 1 requests a full rebind`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(2),
            listOf("target()"),
            lineOffset = -3,
            changedLine = 1,
            isEnterAtLineStart = false,
        )
        assertEquals(TypingShiftResult.RebindAll, result)
    }

    @Test
    fun `revives an invalid tip when the stored line matches again`() {
        val result = DocumentChange.applyTypingLineShift(
            tip(1, isValid = false),
            listOf("target()"),
            lineOffset = 0,
            changedLine = 2,
            isEnterAtLineStart = false,
        )
        val update = result as TypingShiftResult.Update
        assertTrue(update.tip.isValid)
    }
}
